package com.evelin.loganalysis.logcollection.collector;

import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.model.CollectionCheckpoint;
import com.evelin.loganalysis.logcollection.model.CollectionState;
import com.evelin.loganalysis.logcollection.service.CheckpointManager;
import com.evelin.loganalysis.logcollection.model.LogSource;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class SshRemoteCollector extends AbstractLogCollector {

    private Session session;
    private ChannelSftp channelSftp;

    private final Map<String, FileContext> fileContextMap = new ConcurrentHashMap<>();
    private String[] filePatterns;
    private boolean isDirectoryMode;

    private String tempDir;

    private static class FileContext {
        private final String filePath;
        private long filePointer;
        private long currentFileSize;
        private long collectedLines;
        private final StringBuilder multiLineBuffer = new StringBuilder();
        private long multiLineStartLineNumber;
        private final AtomicBoolean active = new AtomicBoolean(false);

        FileContext(String filePath) {
            this.filePath = filePath;
        }

        synchronized void resetBuffer() {
            multiLineBuffer.setLength(0);
            multiLineStartLineNumber = 0;
        }
    }

    public SshRemoteCollector(LogSource logSource,
                              CheckpointManager checkpointManager,
                              CollectionConfig config,
                              com.evelin.loganalysis.logcollection.service.RawLogEventService rawLogEventService,
                              RabbitTemplate rabbitTemplate) {
        super(logSource, checkpointManager, config, rabbitTemplate);
        this.tempDir = "/tmp/ssh_log_" + logSource.getId() + "_" + System.currentTimeMillis();
    }

    @Override
    protected String getThreadNamePrefix() {
        return "ssh-remote-" + getName();
    }

    private List<String> getFilePaths() {
        List<String> pathsList = logSource.getPathsList();
        if (pathsList != null && !pathsList.isEmpty()) {
            return pathsList;
        }
        String legacyPath = logSource.getPath();
        if (legacyPath != null && !legacyPath.isEmpty()) {
            return Collections.singletonList(legacyPath);
        }
        return Collections.emptyList();
    }

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
            log.info("SSH disconnected: name={}", getName());
        } catch (Exception e) {
            log.warn("Error disconnecting SSH: name={}", getName(), e);
        }

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
        CollectionCheckpoint checkpoint = checkpointManager.load(
                logSource.getId().toString(),
                filePath
        );
        FileContext ctx = fileContextMap.get(filePath);
        if (ctx != null && checkpoint != null) {
            ctx.filePointer = checkpoint.getOffset() != null ? checkpoint.getOffset() : 0L;
            ctx.currentFileSize = checkpoint.getFileSize() != null ? checkpoint.getFileSize() : 0L;
            log.info("Loaded checkpoint for SSH file: path={}, offset={}, size={}",
                    filePath, ctx.filePointer, ctx.currentFileSize);
        }
    }

    @Override
    protected void readLoop() {
        log.info("SSH read loop started: paths={}", getFilePaths());

        long lastCheckpointTime = System.currentTimeMillis();
        long linesSinceCheckpoint = 0;

        while (running.get()) {
            try {
                while (paused.get() && running.get()) {
                    Thread.sleep(100);
                }

                if (!running.get()) {
                    break;
                }

                if (fileContextMap.isEmpty()) {
                    Thread.sleep(1000);
                    continue;
                }

                for (Map.Entry<String, FileContext> entry : fileContextMap.entrySet()) {
                    String filePath = entry.getKey();
                    FileContext ctx = entry.getValue();

                    if (!running.get()) {
                        break;
                    }

                    try {
                        processRemoteFile(filePath, ctx);
                    } catch (Exception e) {
                        log.error("Error processing remote file: {}", filePath, e);
                    }
                }

                if (linesSinceCheckpoint >= config.getCheckpointInterval() ||
                        System.currentTimeMillis() - lastCheckpointTime >= config.getCheckpointIntervalMs()) {
                    saveAllCheckpointsAsync();
                    linesSinceCheckpoint = 0;
                    lastCheckpointTime = System.currentTimeMillis();
                }

                Thread.sleep(config.getFileRotateCheckIntervalMs() > 0 ?
                        config.getFileRotateCheckIntervalMs() : 1000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in SSH read loop", e);
                state = CollectionState.ERROR;
                break;
            }
        }

        log.info("SSH read loop stopped: paths={}", getFilePaths());
    }

    private void processRemoteFile(String filePath, FileContext ctx) throws Exception {
        File tempDirFile = new File(tempDir);
        if (!tempDirFile.exists()) {
            tempDirFile.mkdirs();
        }

        String tempFilePath = tempDir + "/" + new File(filePath).getName();

        try {
            channelSftp.get(filePath, tempFilePath);
        } catch (SftpException e) {
            log.error("SFTP exception during download: path={}, sftpErrorId={}, errorMessage='{}'",
                    filePath, e.id, e.getMessage() != null ? e.getMessage() : "null", e);
        }

        File tempFile = new File(tempFilePath);
        if (!tempFile.exists()) {
            log.error("Downloaded file does not exist: localPath={}", tempFilePath);
            return;
        }

        if (tempFile.length() == 0) {
            log.warn("Downloaded file is empty: localPath={}, remotePath={}", tempFilePath, filePath);
            return;
        }

        long currentFileSize = tempFile.length();

        if (currentFileSize < ctx.currentFileSize) {
            log.info("File rotation detected: path={}, oldSize={}, newSize={}",
                    filePath, ctx.currentFileSize, currentFileSize);
            ctx.filePointer = 0;
            ctx.resetBuffer();
        }

        ctx.currentFileSize = currentFileSize;

        if (ctx.filePointer >= currentFileSize) {
            return;
        }

        processFileContent(tempFile, ctx, filePath);

        tempFile.delete();
    }

    private void processFileContent(File file, FileContext ctx, String filePath) throws IOException {
        Charset charset = getCharset();

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(ctx.filePointer);

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

                ctx.filePointer += bytesRead;

                String content = new String(readBuffer, charset);
                lineBuilder.append(content);

                int lineIndex;
                while ((lineIndex = lineBuilder.indexOf("\n")) >= 0) {
                    String line = lineBuilder.substring(0, lineIndex);
                    lineBuilder.delete(0, lineIndex + 1);

                    if (!line.isEmpty()) {
                        long currentLineNumber = ++ctx.collectedLines;
                        collectedLines.incrementAndGet();

                        processMultiLineLogForContext(ctx, line, currentLineNumber, filePath);
                    }
                }
            }

            if (lineBuilder.length() > 0) {
                String remainingLine = lineBuilder.toString();
                if (!remainingLine.isEmpty()) {
                    long currentLineNumber = ++ctx.collectedLines;
                    collectedLines.incrementAndGet();
                    processMultiLineLogForContext(ctx, remainingLine, currentLineNumber, filePath);
                }
            }

            if (!ctx.multiLineBuffer.isEmpty()) {
                flushMultiLineBufferForContext(ctx, filePath);
            }
        }
    }

    private void processMultiLineLogForContext(FileContext ctx, String line, long lineNumber, String filePath) {
        boolean isLogStart = isLogStart(line);

        if (isLogStart) {
            if (!ctx.multiLineBuffer.isEmpty()) {
                String logContent = ctx.multiLineBuffer.toString();
                processLine(logContent, ctx.multiLineStartLineNumber, filePath);
                ctx.multiLineBuffer.setLength(0);
                ctx.multiLineStartLineNumber = 0;
            }
            ctx.multiLineStartLineNumber = lineNumber;
            ctx.multiLineBuffer.append(line);
        } else {
            if (!ctx.multiLineBuffer.isEmpty()) {
                ctx.multiLineBuffer.append("\n");
            }
            ctx.multiLineBuffer.append(line);
        }
    }

    private void flushMultiLineBufferForContext(FileContext ctx, String filePath) {
        if (!running.get()) {
            return;
        }
        if (!ctx.multiLineBuffer.isEmpty()) {
            String logContent = ctx.multiLineBuffer.toString();
            processLine(logContent, ctx.multiLineStartLineNumber, filePath);
            ctx.multiLineBuffer.setLength(0);
            ctx.multiLineStartLineNumber = 0;
        }
    }

    private void saveCheckpointForContextAsync(String filePath, FileContext ctx) {
        try {
            checkpointManager.save(
                    logSource.getId().toString(),
                    filePath,
                    ctx.filePointer,
                    ctx.currentFileSize,
                    "",
                    java.time.LocalDateTime.now()
            );
        } catch (Exception e) {
            log.warn("Failed to save checkpoint for file: {}", filePath, e);
        }
    }

    private void saveAllCheckpointsAsync() {
        for (Map.Entry<String, FileContext> entry : fileContextMap.entrySet()) {
            FileContext ctx = entry.getValue();
            if (ctx.active.get() || ctx.filePointer > 0) {
                saveCheckpointForContextAsync(entry.getKey(), ctx);
            }
        }
    }

    @Override
    protected void saveCheckpointAsync() {
        if (!running.get()) {
            return;
        }
        saveAllCheckpointsAsync();
    }
}
