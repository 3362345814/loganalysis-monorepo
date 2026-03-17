package com.evelin.loganalysis.logcollection.collector;

import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.model.CollectionCheckpoint;
import com.evelin.loganalysis.logcollection.model.CollectionState;
import com.evelin.loganalysis.logcollection.service.CheckpointManager;
import com.evelin.loganalysis.logcollection.model.LogSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;

@Slf4j
public class LocalFileCollector extends AbstractLogCollector {

    private final Map<String, FileContext> fileContextMap = new ConcurrentHashMap<>();

    private String[] filePatterns;
    private boolean isDirectoryMode;

    private static final long DIRECTORY_SCAN_INTERVAL_MS = 5000;

    private Path watchPath;
    private WatchService watchService;

    private ExecutorService watcherExecutor;

    private static class FileContext {
        private final String filePath;
        private RandomAccessFile file;
        private long filePointer;
        private String currentInode;
        private long currentFileSize;
        private long currentFileMtime;
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

    public LocalFileCollector(LogSource logSource,
                              CheckpointManager checkpointManager,
                              CollectionConfig config,
                              com.evelin.loganalysis.logcollection.service.RawLogEventService rawLogEventService,
                              RabbitTemplate rabbitTemplate) {
        super(logSource, checkpointManager, config, rabbitTemplate);
    }

    @Override
    protected String getThreadNamePrefix() {
        return "local-file-" + getName();
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

    private boolean isMultiPathMode() {
        List<String> paths = getFilePaths();
        return paths != null && paths.size() > 1;
    }

    @Override
    protected void doConnect() throws IOException {
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
                FileContext ctx = new FileContext(filePath);
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
                if (!Files.isRegularFile(entry)) {
                    continue;
                }

                String fileName = entry.getFileName().toString();
                if (matchesPattern(fileName)) {
                    String fullPath = entry.toAbsolutePath().toString();
                    if (!fileContextMap.containsKey(fullPath)) {
                        FileContext ctx = new FileContext(fullPath);
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
        if (filePatterns == null || filePatterns.length == 0) {
            return false;
        }

        for (String pattern : filePatterns) {
            if (pattern.contains("*")) {
                String regex = pattern.replace(".", "\\.").replace("*", ".*");
                if (fileName.matches(regex)) {
                    return true;
                }
            } else if (pattern.equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void initExecutors() {
        super.initExecutors();

        watcherExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, getThreadNamePrefix() + "-watcher");
            t.setDaemon(true);
            return t;
        });
    }

    private void loadCheckpointForContext(String filePath) {
        CollectionCheckpoint checkpoint = checkpointManager.load(
                logSource.getId().toString(),
                filePath
        );
        FileContext ctx = fileContextMap.get(filePath);
        if (ctx != null && checkpoint != null) {
            ctx.filePointer = checkpoint.getOffset() != null ? checkpoint.getOffset() : 0L;
            log.info("Loaded checkpoint for file: path={}, offset={}", filePath, ctx.filePointer);
        }
    }

    private void openFileForContext(FileContext ctx) throws IOException {
        Path path = Paths.get(ctx.filePath);
        File fileObj = path.toFile();

        if (!fileObj.exists()) {
            log.warn("Log file not found: {}", ctx.filePath);
            return;
        }

        updateFileInfoForContext(ctx, path);

        ctx.file = new RandomAccessFile(fileObj, "r");
        ctx.file.seek(ctx.filePointer);

        log.info("Opened log file: path={}, offset={}, size={}",
                ctx.filePath, ctx.filePointer, ctx.currentFileSize);

        ctx.active.set(true);
    }

    private void updateFileInfoForContext(FileContext ctx, Path filePath) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            ctx.currentFileSize = attrs.size();
            ctx.currentFileMtime = attrs.lastModifiedTime().toMillis();

            try {
                DosFileAttributes dosAttrs = Files.readAttributes(filePath, DosFileAttributes.class);
                ctx.currentInode = String.valueOf(dosAttrs.fileKey());
            } catch (Exception e) {
                ctx.currentInode = filePath.toString() + "_" + ctx.currentFileSize + "_" + ctx.currentFileMtime;
            }

        } catch (IOException e) {
            log.warn("Failed to read file attributes: path={}", filePath, e);
        }
    }

    private void closeAllFiles() {
        for (Map.Entry<String, FileContext> entry : fileContextMap.entrySet()) {
            FileContext ctx = entry.getValue();
            if (ctx.file != null) {
                try {
                    ctx.file.close();
                } catch (IOException e) {
                    log.warn("Error closing file: {}", entry.getKey(), e);
                }
            }
        }
    }

    private void startFileWatcher() throws IOException {
        if (!config.isEnableFileWatcher()) {
            log.info("File watcher is disabled");
            return;
        }

        if (watchPath == null) {
            log.info("Watch path is null, skipping file watcher");
            return;
        }

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

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Path changedPath = ((WatchEvent<Path>) event).context();

                    if (changedPath.getFileName().toString().equals(
                            Paths.get(getPrimaryFilePath()).getFileName().toString())) {

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

    private void checkFileRotation() {
        Path filePath = Paths.get(getPrimaryFilePath());

        if (!filePath.toFile().exists()) {
            return;
        }

        try {
            long newSize = Files.size(filePath);

            FileContext ctx = fileContextMap.get(filePath.toString());
            if (ctx != null && newSize < ctx.filePointer) {
                log.info("File rotation detected: path={}, oldSize={}, newSize={}",
                        filePath, ctx.filePointer, newSize);
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
            FileContext ctx = fileContextMap.get(newFilePath.toString());
            if (ctx != null && ctx.file != null) {
                ctx.file.close();
            }

            if (ctx != null) {
                ctx.filePointer = 0L;
                updateFileInfoForContext(ctx, newFilePath);
                openFileForContext(ctx);
            }

            log.info("Handled file rotation: path={}", newFilePath);

        } catch (Exception e) {
            log.error("Failed to handle file rotation", e);
            state = CollectionState.ERROR;
        }
    }

    @Override
    protected void readLoop() {
        log.info("Read loop started: paths={}, directoryMode={}", getFilePaths(), isDirectoryMode);

        if (isDirectoryMode || fileContextMap.size() > 1) {
            readLoopMultiFileMode();
        } else {
            readLoopSingleFileMode();
        }

        log.info("Read loop stopped: paths={}", getFilePaths());
    }

    private void readLoopSingleFileMode() {
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
                    Thread.sleep(100);
                    continue;
                }

                for (Map.Entry<String, FileContext> entry : fileContextMap.entrySet()) {
                    FileContext ctx = entry.getValue();

                    if (!ctx.active.get() || ctx.file == null) {
                        continue;
                    }

                    try {
                        readFromFileContext(ctx, entry.getKey());
                    } catch (Exception e) {
                        log.error("Error reading from file: {}", entry.getKey(), e);
                    }
                }

                if (linesSinceCheckpoint >= config.getCheckpointInterval() ||
                        System.currentTimeMillis() - lastCheckpointTime >= config.getCheckpointIntervalMs()) {
                    saveAllCheckpointsAsync();
                    linesSinceCheckpoint = 0;
                    lastCheckpointTime = System.currentTimeMillis();
                }

                Thread.sleep(10);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in read loop", e);
                state = CollectionState.ERROR;
                break;
            }
        }
    }

    private void readLoopMultiFileMode() {
        while (running.get()) {
            try {
                while (paused.get() && running.get()) {
                    Thread.sleep(100);
                }

                if (!running.get()) {
                    break;
                }

                if (fileContextMap.isEmpty()) {
                    Thread.sleep(100);
                    continue;
                }

                for (Map.Entry<String, FileContext> entry : fileContextMap.entrySet()) {
                    FileContext ctx = entry.getValue();

                    if (!ctx.active.get() || ctx.file == null) {
                        continue;
                    }

                    try {
                        readFromFileContext(ctx, entry.getKey());
                    } catch (Exception e) {
                        log.error("Error reading from file: {}", entry.getKey(), e);
                        ctx.active.set(false);
                    }
                }

                Thread.sleep(10);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in directory read loop", e);
                state = CollectionState.ERROR;
                break;
            }
        }
    }

    private void readFromFileContext(FileContext ctx, String filePath) throws IOException {
        File fileObj = new File(filePath);
        if (!fileObj.exists()) {
            log.warn("File no longer exists: {}", filePath);
            ctx.active.set(false);
            return;
        }

        long currentSize = fileObj.length();
        if (currentSize < ctx.currentFileSize) {
            log.info("File rotation detected: {} (old size: {}, new size: {})",
                    filePath, ctx.currentFileSize, currentSize);
            ctx.filePointer = 0;
            ctx.resetBuffer();
        }

        ctx.currentFileSize = currentSize;

        if (ctx.filePointer >= ctx.currentFileSize) {
            return;
        }

        ctx.file.seek(ctx.filePointer);

        StringBuilder lineBuilder = new StringBuilder();
        long lastCheckpointTime = System.currentTimeMillis();
        long linesSinceCheckpoint = 0;

        byte[] readBuffer = new byte[config.getReadBufferSize()];
        int bytesRead = ctx.file.read(readBuffer, 0, readBuffer.length);

        if (bytesRead == -1) {
            return;
        }

        if (bytesRead < readBuffer.length) {
            byte[] trimmedBuffer = new byte[bytesRead];
            System.arraycopy(readBuffer, 0, trimmedBuffer, 0, bytesRead);
            readBuffer = trimmedBuffer;
        }

        String content = new String(readBuffer, getCharset());

        int lastNewlineIndex = content.lastIndexOf('\n');
        if (lastNewlineIndex >= 0) {
            String completeContent = content.substring(0, lastNewlineIndex + 1);
            lineBuilder.append(completeContent);
            ctx.filePointer += (lastNewlineIndex + 1);
        } else {
            lineBuilder.append(content);
            ctx.filePointer += bytesRead;
        }

        int lineIndex;
        while ((lineIndex = lineBuilder.indexOf("\n")) >= 0) {
            String line = lineBuilder.substring(0, lineIndex);
            lineBuilder.delete(0, lineIndex + 1);

            if (!line.isEmpty()) {
                long currentLineNumber = ++ctx.collectedLines;
                collectedLines.incrementAndGet();

                processMultiLineLogForContext(ctx, line, currentLineNumber, filePath);

                linesSinceCheckpoint++;

                if (linesSinceCheckpoint >= config.getCheckpointInterval() ||
                        System.currentTimeMillis() - lastCheckpointTime >= config.getCheckpointIntervalMs()) {
                    saveCheckpointForFileAsync(filePath, ctx);
                    linesSinceCheckpoint = 0;
                    lastCheckpointTime = System.currentTimeMillis();
                }
            }
        }
    }

    private void saveCheckpointForFileAsync(String filePath, FileContext ctx) {
        try {
            checkpointManager.save(
                    logSource.getId().toString(),
                    filePath,
                    ctx.filePointer,
                    ctx.currentFileSize,
                    ctx.currentInode,
                    java.time.LocalDateTime.now()
            );
        } catch (Exception e) {
            log.warn("Failed to save checkpoint for file: {}", filePath, e);
        }
    }

    private void saveAllCheckpointsAsync() {
        for (Map.Entry<String, FileContext> entry : fileContextMap.entrySet()) {
            FileContext ctx = entry.getValue();
            if (ctx.active.get()) {
                saveCheckpointForFileAsync(entry.getKey(), ctx);
            }
        }
    }

    private void processMultiLineLogForContext(FileContext ctx, String line, long lineNumber, String filePath) {
        if (logStartPattern != null) {
            Matcher matcher = logStartPattern.matcher(line);
            if (matcher.find()) {
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
                    ctx.multiLineBuffer.append(line);
                } else {
                    processLine(line, lineNumber, filePath);
                }
            }
        } else {
            if (!ctx.multiLineBuffer.isEmpty()) {
                ctx.multiLineBuffer.append("\n");
                ctx.multiLineBuffer.append(line);
            } else {
                processLine(line, lineNumber, filePath);
            }
        }

        if (!ctx.multiLineBuffer.isEmpty() && ctx.multiLineBuffer.length() > 0) {
            flushMultiLineBufferForContext(ctx, filePath);
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

    @Override
    protected void shutdownExecutors() {
        super.shutdownExecutors();

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
        }
    }
}
