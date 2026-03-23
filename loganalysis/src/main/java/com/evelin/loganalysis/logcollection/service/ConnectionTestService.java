package com.evelin.loganalysis.logcollection.service;

import com.evelin.loganalysis.logcollection.dto.ConnectionTestRequest;
import com.evelin.loganalysis.logcollection.dto.ConnectionTestResponse;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ConnectionTestService {

    private static final int DEFAULT_SSH_PORT = 22;
    private static final int CONNECTION_TIMEOUT = 10000;

    public ConnectionTestResponse testSshConnection(ConnectionTestRequest request) {
        String host = request.getHost();
        Integer port = request.getPort();
        String username = request.getUsername();
        String password = request.getPassword();

        if (host == null || host.isEmpty()) {
            return ConnectionTestResponse.builder()
                    .success(false)
                    .message("主机地址不能为空")
                    .build();
        }
        if (username == null || username.isEmpty()) {
            return ConnectionTestResponse.builder()
                    .success(false)
                    .message("用户名不能为空")
                    .build();
        }
        if (password == null || password.isEmpty()) {
            return ConnectionTestResponse.builder()
                    .success(false)
                    .message("密码不能为空")
                    .build();
        }

        if (port == null || port < 1 || port > 65535) {
            port = DEFAULT_SSH_PORT;
        }

        ConnectionTestResponse.ConnectionTestResponseBuilder responseBuilder = ConnectionTestResponse.builder()
                .host(host)
                .port(port)
                .username(username);

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(CONNECTION_TIMEOUT);
            session.connect();

            Channel channel = session.openChannel("sftp");
            channel.connect(CONNECTION_TIMEOUT);

            responseBuilder
                    .success(true)
                    .message("SSH 连接成功，SFTP 服务可用");

            channel.disconnect();
            session.disconnect();

            log.info("SSH 连接测试成功: host={}, port={}, username={}", host, port, username);
        } catch (JSchException e) {
            log.warn("SSH 连接测试失败: host={}, port={}, username={}, error={}", host, port, username, e.getMessage());
            responseBuilder
                    .success(false)
                    .message("SSH 连接失败: " + e.getMessage());
        } catch (Exception e) {
            log.warn("SFTP 连接测试失败: host={}, port={}, username={}, error={}", host, port, username, e.getMessage());
            responseBuilder
                    .success(false)
                    .message("SFTP 连接失败: " + e.getMessage());
        }

        return responseBuilder.build();
    }

    public ConnectionTestResponse testPathExists(ConnectionTestRequest request) {
        String sourceType = request.getSourceType();
        List<String> paths = request.getPaths();

        if (paths == null || paths.isEmpty()) {
            return ConnectionTestResponse.builder()
                    .success(false)
                    .message("日志路径不能为空")
                    .build();
        }

        ConnectionTestResponse.ConnectionTestResponseBuilder responseBuilder = ConnectionTestResponse.builder()
                .sourceType(sourceType)
                .paths(paths);

        try {
            if ("SSH".equalsIgnoreCase(sourceType)) {
                return testSshPath(request, responseBuilder);
            } else {
                return testLocalPath(paths, responseBuilder);
            }
        } catch (Exception e) {
            log.error("路径验证异常: sourceType={}, paths={}", sourceType, paths, e);
            return responseBuilder
                    .success(false)
                    .message("路径验证失败: " + e.getMessage())
                    .build();
        }
    }

    private ConnectionTestResponse testSshPath(ConnectionTestRequest request,
                                                ConnectionTestResponse.ConnectionTestResponseBuilder responseBuilder) {
        String host = request.getHost();
        Integer port = request.getPort();
        String username = request.getUsername();
        String password = request.getPassword();
        List<String> paths = request.getPaths();

        if (host == null || username == null || password == null) {
            return responseBuilder
                    .success(false)
                    .message("SSH 配置不完整")
                    .build();
        }

        if (port == null) {
            port = DEFAULT_SSH_PORT;
        }

        JSch jsch = new JSch();
        Session session;
        ChannelSftp sftpChannel;
        try {
            session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(CONNECTION_TIMEOUT);
            session.connect();

            Channel channel = session.openChannel("sftp");
            channel.connect(CONNECTION_TIMEOUT);
            sftpChannel = (ChannelSftp) channel;
        } catch (JSchException e) {
            return responseBuilder
                    .success(false)
                    .message("SSH 连接失败: " + e.getMessage())
                    .build();
        }

        Map<String, Boolean> pathResults = new HashMap<>();
        List<String> existingPaths = new ArrayList<>();
        List<String> missingPaths = new ArrayList<>();

        for (String path : paths) {
            if (path == null || path.trim().isEmpty()) {
                continue;
            }
            String trimmedPath = path.trim();
            boolean hasBackslash = trimmedPath.contains("\\");
            String normalizedPath = trimmedPath.replace("\\", "/");

            try {
                sftpChannel.stat(trimmedPath);
                pathResults.put(path, true);
                existingPaths.add(path);
            } catch (SftpException e) {
                pathResults.put(path, false);
                missingPaths.add(path);

                // If backslash path failed, try forward-slash normalization
                if (hasBackslash) {
                    try {
                        sftpChannel.stat(normalizedPath);
                        pathResults.put(path, true);
                        existingPaths.add(path);
                        missingPaths.remove(path);
                    } catch (SftpException e2) {
                        // Normalized path also failed — Windows OpenSSH SFTP home dir restriction.
                        // Try stripping the SFTP home directory prefix to get a relative path.
                        try {
                            String pwd = sftpChannel.pwd();
                            String relativeToHome = normalizedPath;
                            String pwdWithoutSlash = (pwd != null && pwd.startsWith("/")) ? pwd.substring(1) : pwd;
                            if (pwdWithoutSlash != null && normalizedPath.toLowerCase().startsWith(pwdWithoutSlash.toLowerCase())) {
                                relativeToHome = normalizedPath.substring(pwdWithoutSlash.length());
                                if (relativeToHome.startsWith("/")) {
                                    relativeToHome = relativeToHome.substring(1);
                                }
                            }
                            if (relativeToHome.isEmpty()) {
                                relativeToHome = ".";
                            }
                            sftpChannel.stat(relativeToHome);
                            pathResults.put(path, true);
                            existingPaths.add(path);
                            missingPaths.remove(path);
                        } catch (SftpException statRelEx) {
                            // Windows OpenSSH may support /D/ style paths for non-home drives
                            if (normalizedPath.length() >= 3 && normalizedPath.charAt(1) == ':') {
                                String driveLetter = String.valueOf(normalizedPath.charAt(0));
                                String pathAfterDrive = normalizedPath.substring(3);
                                String[] sshDriveFormats = {
                                    "/" + driveLetter + "/" + pathAfterDrive,
                                    "/" + driveLetter.toLowerCase() + "/" + pathAfterDrive,
                                    "/" + driveLetter.toUpperCase() + "/" + pathAfterDrive
                                };
                                for (String sshPath : sshDriveFormats) {
                                    try {
                                        sftpChannel.stat(sshPath);
                                        pathResults.put(path, true);
                                        existingPaths.add(path);
                                        missingPaths.remove(path);
                                        break;
                                    } catch (SftpException ignored) { }
                                }
                            }
                            // Cross-drive paths: SFTP cannot reach them, use SSH exec with Windows cmd
                            if (missingPaths.contains(path)) {
                                String windowsPath = normalizedPath.replace("/", "\\");
                                String cmd = "cmd /c if exist \"" + windowsPath + "\" (echo EXISTS) else (echo MISSING)";
                                try {
                                    Channel execChannel = session.openChannel("exec");
                                    ((ChannelExec) execChannel).setCommand(cmd);
                                    execChannel.setInputStream(null);
                                    InputStream in = execChannel.getInputStream();
                                    execChannel.connect(10000);
                                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                                    StringBuilder output = new StringBuilder();
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        output.append(line);
                                    }
                                    execChannel.disconnect();
                                    if ("EXISTS".equals(output.toString().trim())) {
                                        pathResults.put(path, true);
                                        existingPaths.add(path);
                                        missingPaths.remove(path);
                                    }
                                } catch (Exception execEx) {
                                    log.warn("SSH exec 验证路径失败: path={}, error={}", path, execEx.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        }

        sftpChannel.exit();
        session.disconnect();

        String message;
        if (missingPaths.isEmpty()) {
            message = "所有路径都存在，共 " + existingPaths.size() + " 个路径";
        } else {
            message = "部分路径不存在: " + String.join(", ", missingPaths);
        }

        log.info("SSH 路径验证完成: host={}, paths={}, existing={}, missing={}", host, paths.size(), existingPaths.size(), missingPaths.size());

        return responseBuilder
                .success(missingPaths.isEmpty())
                .message(message)
                .pathResults(pathResults)
                .existingCount(existingPaths.size())
                .missingCount(missingPaths.size())
                .missingPaths(missingPaths)
                .build();
    }

    private ConnectionTestResponse testLocalPath(List<String> paths,
                                                  ConnectionTestResponse.ConnectionTestResponseBuilder responseBuilder) {
        Map<String, Boolean> pathResults = new HashMap<>();
        List<String> existingPaths = new ArrayList<>();
        List<String> missingPaths = new ArrayList<>();

        for (String path : paths) {
            if (path == null || path.trim().isEmpty()) {
                continue;
            }
            File file = new File(path.trim());
            boolean exists = file.exists() && file.isFile();
            pathResults.put(path, exists);
            if (exists) {
                existingPaths.add(path);
            } else {
                missingPaths.add(path);
            }
        }

        String message;
        if (missingPaths.isEmpty()) {
            message = "所有路径都存在，共 " + existingPaths.size() + " 个路径";
        } else {
            message = "部分路径不存在: " + String.join(", ", missingPaths);
        }

        log.info("本地路径验证完成: paths={}, existing={}, missing={}", paths.size(), existingPaths.size(), missingPaths.size());

        return responseBuilder
                .success(missingPaths.isEmpty())
                .message(message)
                .pathResults(pathResults)
                .existingCount(existingPaths.size())
                .missingCount(missingPaths.size())
                .missingPaths(missingPaths)
                .build();
    }
}
