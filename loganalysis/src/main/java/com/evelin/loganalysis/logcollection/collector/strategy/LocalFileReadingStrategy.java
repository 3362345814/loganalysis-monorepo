package com.evelin.loganalysis.logcollection.collector.strategy;

import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.model.CollectionCheckpoint;
import com.evelin.loganalysis.logcollection.model.LogSource;
import com.evelin.loganalysis.logcollection.service.CheckpointManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地文件读取策略实现。
 * <p>
 * 支持直接读取本地文件、使用 WatchService 监听文件变化。
 * 继承自 {@link AbstractLogFileReaderStrategy}，只需实现文件读取特有逻辑。
 */
@Slf4j
public class LocalFileReadingStrategy extends AbstractLogFileReaderStrategy {

    // ==================== 本地文件特有组件 ====================
    private final Map<String, LocalFileContext> fileContextMap = new ConcurrentHashMap<>();
    private String[] filePatterns;
    private boolean isDirectoryMode;

    private Path watchPath;
    private WatchService watchService;
    private ExecutorService watcherExecutor;

    // ==================== 内部类：本地文件上下文 ====================

    /**
     * 本地文件专用的 FileContext 子类，包含 RandomAccessFile 句柄和 inode 信息
     */
    private static class LocalFileContext extends FileContext {
        private RandomAccessFile file;
        private String currentInode;
        private long currentFileMtime;
        private final StringBuilder pendingLineBuffer = new StringBuilder();

        LocalFileContext(String filePath) {
            super(filePath);
        }

        RandomAccessFile getFile() {
            return file;
        }

        void setFile(RandomAccessFile file) {
            this.file = file;
        }

        String getCurrentInode() {
            return currentInode;
        }

        void setCurrentInode(String currentInode) {
            this.currentInode = currentInode;
        }

        long getCurrentFileMtime() {
            return currentFileMtime;
        }

        void setCurrentFileMtime(long currentFileMtime) {
            this.currentFileMtime = currentFileMtime;
        }

        synchronized void closeFile() {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    // ignore
                }
                file = null;
            }
        }

        StringBuilder getPendingLineBuffer() {
            return pendingLineBuffer;
        }

        void resetPendingLineBuffer() {
            pendingLineBuffer.setLength(0);
        }
    }

    // ==================== 构造函数 ====================

    public LocalFileReadingStrategy(LogSource logSource,
                                    CheckpointManager checkpointManager,
                                    CollectionConfig config,
                                    RabbitTemplate rabbitTemplate,
                                    AtomicBoolean running,
                                    AtomicBoolean paused,
                                    AtomicLong collectedLines) {
        super(logSource, checkpointManager, config, rabbitTemplate, running, paused, collectedLines);
    }

    // ==================== 生命周期方法 ====================

    @Override
    protected void doConnect() throws IOException {
        // 本地文件无需连接
    }

    @Override
    protected void doDisconnect() {
        closeWatchService();
        closeAllFiles();
    }

    @Override
    protected void doInitFiles() throws IOException {
        List<String> filePaths = getFilePaths();
        if (filePaths.isEmpty()) {
            throw new IllegalArgumentException("No file paths configured for collection");
        }

        initMultiPathMode(filePaths);

        if (isDirectoryMode) {
            scanAndInitFiles();
        } else {
            initAllFiles(filePaths);
        }

        if (!filePaths.isEmpty()) {
            watchPath = Paths.get(filePaths.get(0)).getParent();
        }

        if (config.isEnableFileWatcher() && watchPath != null) {
            startFileWatcher();
        }
    }

    @Override
    protected void doReadFiles() throws Exception {
        checkFileRotation();

        for (Map.Entry<String, LocalFileContext> entry : fileContextMap.entrySet()) {
            String filePath = entry.getKey();
            LocalFileContext ctx = entry.getValue();
            if (!running.get()) break;

            try {
                readFromFileContext(ctx, filePath);
            } catch (Exception e) {
                log.error("Error processing file: {}", filePath, e);
            }
        }
    }

    // ==================== 文件初始化 ====================

    private void initMultiPathMode(List<String> filePaths) {
        if (filePaths.size() > 1) {
            isDirectoryMode = false;
            log.info("Multi-path mode enabled: {} files", filePaths.size());
        } else {
            String singlePath = filePaths.get(0);
            File pathFile = new File(singlePath);
            if (pathFile.isDirectory()) {
                isDirectoryMode = true;
                filePatterns = null;
                log.info("Directory mode enabled: path={}", singlePath);
            } else {
                isDirectoryMode = false;
                log.info("Single file mode: path={}", singlePath);
            }
        }
    }

    private void initAllFiles(List<String> filePaths) {
        for (String filePath : filePaths) {
            if (!fileContextMap.containsKey(filePath)) {
                LocalFileContext ctx = new LocalFileContext(filePath);
                fileContextMap.put(filePath, ctx);
                loadCheckpointForContext(filePath);
                try {
                    openFileForContext(ctx);
                    log.info("Initialized file for collection: {}", filePath);
                } catch (IOException e) {
                    log.error("Failed to open file: {}", filePath, e);
                }
            }
        }
    }

    private void scanAndInitFiles() {
        List<String> filePaths = getFilePaths();
        String dirPath = filePaths.isEmpty() ? logSource.getPath() : filePaths.get(0);

        if (filePaths.size() == 1 && new File(filePaths.get(0)).isDirectory()) {
            dirPath = filePaths.get(0);
        }

        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("Directory does not exist or is not a directory: {}", dirPath);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir.toPath())) {
            for (Path entry : stream) {
                if (!Files.isRegularFile(entry)) continue;

                String fileName = entry.getFileName().toString();
                if (matchesPattern(fileName)) {
                    String fullPath = entry.toAbsolutePath().toString();
                    if (!fileContextMap.containsKey(fullPath)) {
                        LocalFileContext ctx = new LocalFileContext(fullPath);
                        fileContextMap.put(fullPath, ctx);
                        loadCheckpointForContext(fullPath);
                        openFileForContext(ctx);
                        log.info("Initialized file for collection: {}", fullPath);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan directory: {}", dirPath, e);
        }
    }

    private boolean matchesPattern(String fileName) {
        if (filePatterns == null || filePatterns.length == 0) return false;
        for (String pattern : filePatterns) {
            if (pattern.contains("*")) {
                String regex = pattern.replace(".", "\\.").replace("*", ".*");
                if (fileName.matches(regex)) return true;
            } else if (pattern.equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 检查点与文件句柄 ====================

    private void loadCheckpointForContext(String filePath) {
        CollectionCheckpoint checkpoint = checkpointManager.load(id, filePath);
        LocalFileContext ctx = fileContextMap.get(filePath);
        if (ctx != null && checkpoint != null) {
            ctx.setFilePointer(checkpoint.getOffset() != null ? checkpoint.getOffset() : 0L);
            log.info("Loaded checkpoint for file: path={}, offset={}", filePath, ctx.getFilePointer());
        }
    }

    private void openFileForContext(LocalFileContext ctx) throws IOException {
        Path path = Paths.get(ctx.getFilePath());
        File fileObj = path.toFile();

        if (!fileObj.exists()) {
            log.warn("Log file not found: {}", ctx.getFilePath());
            return;
        }

        updateFileInfoForContext(ctx, path);
        ctx.setFile(new RandomAccessFile(fileObj, "r"));
        ctx.getFile().seek(ctx.getFilePointer());

        log.info("Opened log file: path={}, offset={}, size={}",
                ctx.getFilePath(), ctx.getFilePointer(), ctx.getCurrentFileSize());
        ctx.setActive(true);
    }

    private void updateFileInfoForContext(LocalFileContext ctx, Path filePath) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            ctx.setCurrentFileSize(attrs.size());
            ctx.setCurrentFileMtime(attrs.lastModifiedTime().toMillis());

            try {
                DosFileAttributes dosAttrs = Files.readAttributes(filePath, DosFileAttributes.class);
                ctx.setCurrentInode(String.valueOf(dosAttrs.fileKey()));
            } catch (Exception e) {
                ctx.setCurrentInode(filePath.toString() + "_" + ctx.getCurrentFileSize() + "_" + ctx.getCurrentFileMtime());
            }
        } catch (IOException e) {
            log.warn("Failed to read file attributes: path={}", filePath, e);
        }
    }

    // ==================== 文件读取 ====================

    @Override
    public void readFromFileContext(FileContext fileContext, String filePath) throws IOException {
        LocalFileContext ctx = (LocalFileContext) fileContext;
        File fileObj = new File(filePath);

        if (!fileObj.exists()) {
            log.warn("File no longer exists: {}", filePath);
            ctx.setActive(false);
            return;
        }

        long currentSize = fileObj.length();

        if (currentSize < ctx.getCurrentFileSize()) {
            log.info("File rotation detected: {} (old size: {}, new size: {})",
                    filePath, ctx.getCurrentFileSize(), currentSize);
            ctx.setFilePointer(0);
            ctx.resetBuffer();
            ctx.resetPendingLineBuffer();
        }

        ctx.setCurrentFileSize(currentSize);

        if (ctx.getFilePointer() >= ctx.getCurrentFileSize()) {
            return;
        }

        ctx.getFile().seek(ctx.getFilePointer());

        byte[] readBuffer = new byte[config.getReadBufferSize()];
        int bytesRead = ctx.getFile().read(readBuffer, 0, readBuffer.length);

        if (bytesRead == -1) {
            return;
        }

        if (bytesRead < readBuffer.length) {
            byte[] trimmedBuffer = new byte[bytesRead];
            System.arraycopy(readBuffer, 0, trimmedBuffer, 0, bytesRead);
            readBuffer = trimmedBuffer;
        }

        ctx.setFilePointer(ctx.getFilePointer() + bytesRead);

        String content = new String(readBuffer, charset);
        StringBuilder lineBuilder = ctx.getPendingLineBuffer();
        lineBuilder.append(content);

        int lineIndex;
        while ((lineIndex = lineBuilder.indexOf("\n")) >= 0) {
            String line = lineBuilder.substring(0, lineIndex);
            lineBuilder.delete(0, lineIndex + 1);

            // 兼容 Windows 换行符 \r\n
            if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                line = line.substring(0, line.length() - 1);
            }

            if (!line.isEmpty()) {
                long currentLineNumber = ++ctx.collectedLines;
                handleLine(line, currentLineNumber, filePath, ctx.getFilePointer());
            }
        }
    }

    // ==================== 文件轮转检测 ====================

    @Override
    public void checkFileRotation() {
        Path filePath = Paths.get(getPrimaryFilePath());

        if (!filePath.toFile().exists()) {
            return;
        }

        try {
            long newSize = Files.size(filePath);
            LocalFileContext ctx = fileContextMap.get(filePath.toString());

            if (ctx != null && newSize < ctx.getFilePointer()) {
                log.info("File rotation detected: path={}, oldSize={}, newSize={}",
                        filePath, ctx.getFilePointer(), newSize);
                handleFileRotation(filePath);
            }

            if (ctx != null) {
                updateFileInfoForContext(ctx, filePath);
            }
        } catch (IOException e) {
            log.warn("Failed to check file rotation: path={}", filePath, e);
        }
    }

    private void handleFileRotation(Path newFilePath) {
        try {
            LocalFileContext ctx = fileContextMap.get(newFilePath.toString());
            if (ctx != null) {
                ctx.closeFile();
                ctx.setFilePointer(0L);
                updateFileInfoForContext(ctx, newFilePath);
                openFileForContext(ctx);
            }
            log.info("Handled file rotation: path={}", newFilePath);
        } catch (Exception e) {
            log.error("Failed to handle file rotation", e);
            state = com.evelin.loganalysis.logcollection.model.CollectionState.ERROR;
        }
    }

    // ==================== WatchService ====================

    private void startFileWatcher() throws IOException {
        if (!config.isEnableFileWatcher()) {
            log.info("File watcher is disabled");
            return;
        }
        if (watchPath == null) {
            log.info("Watch path is null, skipping file watcher");
            return;
        }

        watcherExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "local-file-" + logSource.getName() + "-watcher");
            t.setDaemon(true);
            return t;
        });

        watchService = FileSystems.getDefault().newWatchService();
        watchPath.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

        watcherExecutor.submit(this::watchLoop);
        log.info("Started file watcher: path={}", watchPath);
    }

    private void closeWatchService() {
        if (watchService != null) {
            try {
                watchService.close();
                log.info("Closed watch service");
            } catch (IOException e) {
                log.warn("Error closing watch service", e);
            } finally {
                watchService = null;
            }
        }
        if (watcherExecutor != null && !watcherExecutor.isShutdown()) {
            watcherExecutor.shutdownNow();
            try {
                if (!watcherExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Watcher executor not terminated in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                watcherExecutor.shutdownNow();
            }
            watcherExecutor = null;
        }
    }

    private void watchLoop() {
        log.info("File watcher loop started: path={}", watchPath);

        while (running.get()) {
            try {
                WatchKey key = watchService.poll(config.getFileRotateCheckIntervalMs(), TimeUnit.MILLISECONDS);
                if (key == null) {
                    checkFileRotation();
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    Path changedPath = ((WatchEvent<Path>) event).context();
                    String primaryName = Paths.get(getPrimaryFilePath()).getFileName().toString();

                    if (changedPath.getFileName().toString().equals(primaryName)) {
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            handleFileCreated(changedPath);
                        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            handleFileModified(changedPath);
                        }
                    }
                }

                key.reset();
                checkFileRotation();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in file watcher loop", e);
            }
        }
        log.info("File watcher loop stopped");
    }

    private void handleFileCreated(Path changedPath) {
        log.info("New file detected (possible rotation): path={}", changedPath);
        checkFileRotation();
    }

    private void handleFileModified(Path changedPath) {
        Path currentPath = Paths.get(getPrimaryFilePath());
        if (!currentPath.toFile().exists()) {
            log.info("Original file deleted, treating as rotation");
            checkFileRotation();
        }
    }

    // ==================== 文件操作辅助 ====================

    @Override
    public void closeAllFiles() {
        for (Map.Entry<String, LocalFileContext> entry : fileContextMap.entrySet()) {
            entry.getValue().closeFile();
        }
    }

    @Override
    public boolean fileExists(String filePath) {
        return new File(filePath).exists();
    }

    @Override
    public long getFileSize(String filePath) throws IOException {
        return Files.size(Paths.get(filePath));
    }

    @Override
    public long getFilePointer(String filePath) {
        LocalFileContext ctx = fileContextMap.get(filePath);
        return ctx != null ? ctx.getFilePointer() : 0L;
    }

    @Override
    public boolean isFileActive(String filePath) {
        LocalFileContext ctx = fileContextMap.get(filePath);
        return ctx != null && ctx.isActive();
    }

    // ==================== 检查点保存 ====================

    @Override
    public void saveCheckpoint(String filePath, FileContext fileContext) {
        LocalFileContext ctx = (LocalFileContext) fileContext;
        try {
            checkpointManager.save(
                    id, filePath,
                    ctx.getFilePointer(),
                    ctx.getCurrentFileSize(),
                    ctx.getCurrentInode() != null ? ctx.getCurrentInode() : "",
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            log.warn("Failed to save checkpoint for file: {}", filePath, e);
        }
    }

    @Override
    public void saveAllCheckpoints() {
        for (Map.Entry<String, LocalFileContext> entry : fileContextMap.entrySet()) {
            LocalFileContext ctx = entry.getValue();
            if (ctx.isActive()) {
                saveCheckpoint(entry.getKey(), ctx);
            }
        }
        // 刷新多行缓冲
        flushBuffer(getPrimaryFilePath());
    }

    // ==================== 线程名前缀 ====================

    @Override
    protected String getThreadNamePrefix() {
        return "local-file-" + getName();
    }

    // ==================== 公开访问器 ====================

    public Map<String, LocalFileContext> getFileContextMap() {
        return fileContextMap;
    }

    public boolean isDirectoryMode() {
        return isDirectoryMode;
    }
}
