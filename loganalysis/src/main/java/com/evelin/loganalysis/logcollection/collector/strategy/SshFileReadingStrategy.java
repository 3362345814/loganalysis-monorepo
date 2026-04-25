package com.evelin.loganalysis.logcollection.collector.strategy;

import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.model.CollectionCheckpoint;
import com.evelin.loganalysis.logcollection.model.LogSource;
import com.evelin.loganalysis.logcollection.service.CheckpointManager;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


/**
 * SSH 远程文件读取策略实现。
 * <p>
 * 通过 SFTP 协议下载远程文件到本地临时目录后读取。
 * 继承自 {@link AbstractLogFileReaderStrategy}，只需实现文件读取特有逻辑。
 */
@Slf4j
public class SshFileReadingStrategy extends AbstractLogFileReaderStrategy {

    // ==================== SSH 连接资源 ====================
    private Session session;
    private ChannelSftp channelSftp;
    private final String tempDir;

    // ==================== 文件上下文 ====================
    private final Map<String, FileContext> fileContextMap = new ConcurrentHashMap<>();
    private String[] filePatterns;
    private boolean isDirectoryMode;

    // ==================== 构造函数 ====================

    public SshFileReadingStrategy(LogSource logSource,
                                  CheckpointManager checkpointManager,
                                  CollectionConfig config,
                                  RabbitTemplate rabbitTemplate,
                                  AtomicBoolean running,
                                  AtomicBoolean paused,
                                  AtomicLong collectedLines) {
        super(logSource, checkpointManager, config, rabbitTemplate, running, paused, collectedLines);
        this.tempDir = "/tmp/ssh_log_" + logSource.getId() + "_" + System.currentTimeMillis();
    }

    // ==================== 生命周期方法 ====================

    @Override
    protected void doConnect() throws JSchException, SftpException {
        JSch jsch = new JSch();

        String host = logSource.getHost();
        int port = logSource.getPort() != null ? logSource.getPort() : 22;
        String username = logSource.getUsername();
        String password = logSource.getPassword();

        log.info("Connecting to SSH: host={}, port={}, username={}", host, port, username);

        session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(30000);
        session.connect();

        Channel channel = session.openChannel("sftp");
        channel.connect(30000);
        channelSftp = (ChannelSftp) channel;

        log.info("SSH connected successfully: host={}", host);
    }

    @Override
    protected void doDisconnect() {
        try {
            if (channelSftp != null) {
                channelSftp.exit();
                channelSftp = null;
            }
            if (session != null) {
                session.disconnect();
                session = null;
            }
            log.info("SSH disconnected: name={}", logSource.getName());
        } catch (Exception e) {
            log.warn("Error disconnecting SSH: name={}", logSource.getName(), e);
        }

        // 清理临时目录
        File tempDirFile = new File(tempDir);
        if (tempDirFile.exists()) {
            File[] files = tempDirFile.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            tempDirFile.delete();
        }
    }

    @Override
    protected void doInitFiles() throws Exception {
        List<String> filePaths = getFilePaths();

        if (filePaths.isEmpty()) {
            throw new IllegalArgumentException("No file paths configured for collection");
        }

        initMultiPathMode(filePaths);

        for (String filePath : filePaths) {
            if (!fileContextMap.containsKey(filePath)) {
                FileContext ctx = new FileContext(filePath);
                fileContextMap.put(filePath, ctx);
                loadCheckpointForContext(filePath);
                log.info("Initialized file for SSH collection: {}", filePath);
            }
        }

        log.info("SSH collector initialized: {} files, directoryMode={}", fileContextMap.size(), isDirectoryMode);
    }

    @Override
    protected void doReadFiles() throws Exception {
        for (Map.Entry<String, FileContext> entry : fileContextMap.entrySet()) {
            String filePath = entry.getKey();
            FileContext ctx = entry.getValue();
            if (!running.get()) break;

            try {
                readFromFileContext(ctx, filePath);
            } catch (Exception e) {
                log.error("Error processing file: {}", filePath, e);
            }
        }
    }

    // ==================== 文件路径初始化 ====================

    private void initMultiPathMode(List<String> filePaths) {
        if (filePaths.size() > 1) {
            isDirectoryMode = false;
            log.info("Multi-path mode enabled for SSH: {} files", filePaths.size());
        } else {
            isDirectoryMode = false;
            log.info("Single file mode for SSH: path={}", filePaths.get(0));
        }
    }

    private void loadCheckpointForContext(String filePath) {
        CollectionCheckpoint checkpoint = checkpointManager.load(id, filePath);
        FileContext ctx = fileContextMap.get(filePath);
        if (ctx != null && checkpoint != null) {
            ctx.setFilePointer(checkpoint.getOffset() != null ? checkpoint.getOffset() : 0L);
            ctx.setCurrentFileSize(checkpoint.getFileSize() != null ? checkpoint.getFileSize() : 0L);
            log.info("Loaded checkpoint for SSH file: path={}, offset={}, size={}",
                    filePath, ctx.getFilePointer(), ctx.getCurrentFileSize());
        }
    }

    // ==================== 文件轮转检测 ====================

    @Override
    public void checkFileRotation() {
        // SSH 采集通过 processRemoteFile 中的文件大小比较检测轮转
        // 此方法可扩展为通过 SFTP stat 命令检查远程文件
    }

    // ==================== 文件读取 ====================

    @Override
    public void readFromFileContext(FileContext fileContext, String filePath) throws Exception {
        FileContext ctx = fileContext;
        processRemoteFile(filePath, ctx);
    }

    private void processRemoteFile(String filePath, FileContext ctx) throws Exception {
        File tempDirFile = new File(tempDir);
        if (!tempDirFile.exists()) {
            tempDirFile.mkdirs();
        }

        String tempFilePath = tempDir + "/" + remoteFileName(filePath);

        if (!downloadViaSftp(filePath, tempFilePath) && !downloadViaSshExec(filePath, tempFilePath)) {
            return;
        }

        File tempFile = new File(tempFilePath);
        if (!tempFile.exists()) {
            log.error("Downloaded file does not exist: localPath={}", tempFilePath);
            return;
        }

        if (tempFile.length() == 0) {
            return;
        }

        long currentFileSize = tempFile.length();

        if (currentFileSize < ctx.getCurrentFileSize()) {
            log.info("File rotation detected: path={}, oldSize={}, newSize={}",
                    filePath, ctx.getCurrentFileSize(), currentFileSize);
            ctx.setFilePointer(0);
            ctx.resetBuffer();
        }

        ctx.setCurrentFileSize(currentFileSize);

        if (ctx.getFilePointer() >= currentFileSize) {
            return;
        }

        processFileContent(tempFile, ctx, filePath);

        // 处理完成后删除临时文件
        tempFile.delete();
    }

    private boolean downloadViaSftp(String filePath, String tempFilePath) {
        String pwd = null;
        try {
            pwd = channelSftp.pwd();
        } catch (SftpException e) {
            log.debug("Failed to get SFTP working directory: {}", e.getMessage());
        }

        SftpException lastException = null;
        for (String candidate : buildSftpPathCandidates(filePath, pwd)) {
            try {
                channelSftp.get(candidate, tempFilePath);
                if (!candidate.equals(filePath)) {
                    log.info("SFTP download succeeded with normalized path: originalPath={}, downloadPath={}",
                            filePath, candidate);
                }
                return true;
            } catch (SftpException e) {
                lastException = e;
                log.debug("SFTP download candidate failed: originalPath={}, candidatePath={}, sftpErrorId={}, error='{}'",
                        filePath, candidate, e.id, e.getMessage() != null ? e.getMessage() : "null");
            }
        }

        if (lastException != null) {
            log.error("SFTP exception during download: path={}, triedPaths={}, sftpErrorId={}, errorMessage='{}'",
                    filePath, buildSftpPathCandidates(filePath, pwd), lastException.id,
                    lastException.getMessage() != null ? lastException.getMessage() : "null", lastException);
        }
        return false;
    }

    private boolean downloadViaSshExec(String filePath, String tempFilePath) {
        if (session == null || !session.isConnected()) {
            return false;
        }

        List<String> commands = buildExecDownloadCommands(filePath);
        for (String command : commands) {
            try {
                ExecDownloadResult result = runExecDownload(command, tempFilePath);
                File downloadedFile = new File(tempFilePath);
                if (result.exitStatus == 0 && downloadedFile.exists()) {
                    log.info("SSH exec download succeeded: path={}, bytes={}", filePath, downloadedFile.length());
                    return true;
                }
                log.warn("SSH exec download failed: path={}, exitStatus={}, stderr={}",
                        filePath, result.exitStatus, result.stderr);
            } catch (Exception e) {
                log.warn("SSH exec download command failed: path={}, error={}", filePath, e.getMessage());
            }
        }
        return false;
    }

    private ExecDownloadResult runExecDownload(String command, String tempFilePath) throws Exception {
        ChannelExec execChannel = (ChannelExec) session.openChannel("exec");
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        execChannel.setCommand(command);
        execChannel.setInputStream(null);
        execChannel.setErrStream(stderr);

        try (InputStream in = execChannel.getInputStream();
             FileOutputStream out = new FileOutputStream(tempFilePath)) {
            execChannel.connect(30000);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            execChannel.disconnect();
        }

        return new ExecDownloadResult(execChannel.getExitStatus(), stderr.toString(charset));
    }

    private List<String> buildExecDownloadCommands(String filePath) {
        List<String> commands = new ArrayList<>();
        if (isWindowsPath(filePath)) {
            commands.add("powershell -NoProfile -NonInteractive -Command \""
                    + "$bytes=[System.IO.File]::ReadAllBytes('" + escapePowerShellSingleQuoted(filePath) + "'); "
                    + "[Console]::OpenStandardOutput().Write($bytes,0,$bytes.Length)\"");
            commands.add("cmd /c type \"" + filePath.replace("\"", "\"\"") + "\"");
        } else {
            commands.add("cat -- '" + filePath.replace("'", "'\\''") + "'");
        }
        return commands;
    }

    private boolean isWindowsPath(String filePath) {
        return filePath != null && (filePath.matches("^[a-zA-Z]:[\\\\/].*") || filePath.contains("\\"));
    }

    private String escapePowerShellSingleQuoted(String value) {
        return value.replace("'", "''");
    }

    private record ExecDownloadResult(int exitStatus, String stderr) {
    }

    static List<String> buildSftpPathCandidates(String filePath, String pwd) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (filePath == null || filePath.trim().isEmpty()) {
            return new ArrayList<>(candidates);
        }

        String trimmedPath = filePath.trim();
        String normalizedPath = trimmedPath.replace("\\", "/");
        candidates.add(trimmedPath);
        candidates.add(normalizedPath);

        String pwdWithoutSlash = pwd != null && pwd.startsWith("/") ? pwd.substring(1) : pwd;
        if (pwdWithoutSlash != null && !pwdWithoutSlash.isEmpty()
                && normalizedPath.toLowerCase().startsWith(pwdWithoutSlash.toLowerCase())) {
            String relativeToHome = normalizedPath.substring(pwdWithoutSlash.length());
            if (relativeToHome.startsWith("/")) {
                relativeToHome = relativeToHome.substring(1);
            }
            if (!relativeToHome.isEmpty()) {
                candidates.add(relativeToHome);
            }
        }

        if (normalizedPath.length() >= 3 && normalizedPath.charAt(1) == ':' && normalizedPath.charAt(2) == '/') {
            String driveLetter = String.valueOf(normalizedPath.charAt(0));
            String pathAfterDrive = normalizedPath.substring(3);
            candidates.add("/" + driveLetter + "/" + pathAfterDrive);
            candidates.add("/" + driveLetter.toLowerCase() + "/" + pathAfterDrive);
            candidates.add("/" + driveLetter.toUpperCase() + "/" + pathAfterDrive);
        }

        return new ArrayList<>(candidates);
    }

    private String remoteFileName(String filePath) {
        String normalizedPath = filePath == null ? "remote.log" : filePath.replace("\\", "/");
        int lastSlash = normalizedPath.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalizedPath.substring(lastSlash + 1) : normalizedPath;
        if (fileName.isBlank()) {
            fileName = "remote.log";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void processFileContent(File file, FileContext ctx, String filePath) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(ctx.getFilePointer());

            StringBuilder lineBuilder = new StringBuilder();
            byte[] readBuffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = raf.read(readBuffer, 0, readBuffer.length)) != -1) {
                if (!running.get()) {
                    break;
                }

                if (bytesRead < readBuffer.length) {
                    byte[] trimmedBuffer = new byte[bytesRead];
                    System.arraycopy(readBuffer, 0, trimmedBuffer, 0, bytesRead);
                    readBuffer = trimmedBuffer;
                }

                ctx.setFilePointer(ctx.getFilePointer() + bytesRead);

                String content = new String(readBuffer, charset);
                lineBuilder.append(content);

                int lineIndex;
                while ((lineIndex = lineBuilder.indexOf("\n")) >= 0) {
                    String line = lineBuilder.substring(0, lineIndex);
                    lineBuilder.delete(0, lineIndex + 1);

                    if (!line.isEmpty()) {
                        long currentLineNumber = ++ctx.collectedLines;
                        collectedLines.incrementAndGet();
                        handleLine(line, currentLineNumber, filePath, ctx.getFilePointer());
                    }
                }
            }

            if (!lineBuilder.isEmpty()) {
                String remainingLine = lineBuilder.toString();
                if (!remainingLine.isEmpty()) {
                    long currentLineNumber = ++ctx.collectedLines;
                    collectedLines.incrementAndGet();
                    handleLine(remainingLine, currentLineNumber, filePath, ctx.getFilePointer());
                }
            }

            if (!ctx.getMultiLineBuffer().isEmpty()) {
                handleLine("", -1, filePath, ctx.getFilePointer());
            }
        }
    }

    // ==================== 文件操作辅助 ====================

    @Override
    public void closeAllFiles() {
        // SSH 模式下没有本地文件需要关闭
        // 资源清理在 disconnect() 中处理
    }

    @Override
    public boolean fileExists(String filePath) {
        // SSH 模式下通过 SFTP 检查文件存在性，返回 true 由 download 过程处理
        return true;
    }

    @Override
    public long getFileSize(String filePath) throws IOException {
        FileContext ctx = fileContextMap.get(filePath);
        return ctx != null ? ctx.getCurrentFileSize() : 0L;
    }

    @Override
    public long getFilePointer(String filePath) {
        FileContext ctx = fileContextMap.get(filePath);
        return ctx != null ? ctx.getFilePointer() : 0L;
    }

    @Override
    public boolean isFileActive(String filePath) {
        FileContext ctx = fileContextMap.get(filePath);
        return ctx != null && ctx.isActive();
    }

    // ==================== 检查点保存 ====================

    @Override
    public void saveCheckpoint(String filePath, FileContext ctx) {
        try {
            checkpointManager.save(
                    id,
                    filePath,
                    ctx.getFilePointer(),
                    ctx.getCurrentFileSize(),
                    "",
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            log.warn("Failed to save checkpoint for file: {}", filePath, e);
        }
    }

    @Override
    public void saveAllCheckpoints() {
        for (Map.Entry<String, FileContext> entry : fileContextMap.entrySet()) {
            FileContext ctx = entry.getValue();
            if (ctx.isActive() || ctx.getFilePointer() > 0) {
                saveCheckpoint(entry.getKey(), ctx);
            }
        }
        flushBuffer(getPrimaryFilePath());
    }

    // ==================== 线程名前缀 ====================

    @Override
    protected String getThreadNamePrefix() {
        return "ssh-file-" + getName();
    }

    // ==================== 公开访问器 ====================

    public Map<String, FileContext> getFileContextMap() {
        return fileContextMap;
    }

    public boolean isDirectoryMode() {
        return isDirectoryMode;
    }

    public String getTempDir() {
        return tempDir;
    }
}
