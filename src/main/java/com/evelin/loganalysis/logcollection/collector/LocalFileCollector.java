package com.evelin.loganalysis.logcollection.collector;

import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.config.RabbitMQConfig;
import com.evelin.loganalysis.logcollection.dto.LogDesensitizationMessage;
import com.evelin.loganalysis.logcollection.model.CollectionCheckpoint;
import com.evelin.loganalysis.logcollection.model.CollectionState;
import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logcollection.service.CheckpointManager;
import com.evelin.loganalysis.logcollection.service.RawLogEventService;
import com.evelin.loganalysis.logcommon.enums.LogFormat;
import com.evelin.loganalysis.logcommon.enums.LogSourceType;
import com.evelin.loganalysis.logcommon.model.LogSource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地文件日志采集器
 * <p>
 * 基于Java NIO实现高效的本地文件日志采集，支持：
 * - 断点续读（从上次停止位置继续读取）
 * - 文件轮转检测（自动检测日志文件轮转）
 * - 实时文件监听（使用WatchService监听文件变化）
 * - 背压处理（有界队列缓冲采集任务）
 * <p>
 * 注意：此类不是Spring Bean，由外部（如 LocalFileCollectorFactory）负责创建和管理
 *
 * @author Evelin
 */
@Slf4j
public class LocalFileCollector implements LogCollector {

    /**
     * 采集器ID
     */
    private final String id;

    /**
     * 日志源配置
     * -- GETTER --
     * 获取日志源配置
     */
    @Getter
    private final LogSource logSource;

    /**
     * 检查点管理器
     */
    private final CheckpointManager checkpointManager;

    /**
     * 采集配置
     */
    private final CollectionConfig config;

    /**
     * 当前状态
     */
    private volatile CollectionState state = CollectionState.STOPPED;

    /**
     * 运行标志
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 暂停标志
     */
    private final AtomicBoolean paused = new AtomicBoolean(false);

    /**
     * 已采集行数
     */
    private final AtomicLong collectedLines = new AtomicLong(0);

    /**
     * 多行日志缓冲区
     */
    private final StringBuilder multiLineBuffer = new StringBuilder();

    /**
     * 多行日志开始行号
     */
    private long multiLineStartLineNumber = 0;

    /**
     * 日志格式检测模式
     */
    private Pattern logStartPattern;

    /**
     * 文件通道
     */
    private RandomAccessFile file;

    /**
     * 文件路径
     */
    private Path watchPath;

    /**
     * 当前文件偏移量
     */
    private volatile long filePointer;

    /**
     * 当前文件inode
     */
    private volatile String currentInode;

    /**
     * 当前文件大小
     */
    private volatile long currentFileSize;

    /**
     * 当前文件修改时间
     */
    private volatile long currentFileMtime;

    /**
     * 采集队列（生产者-消费者模式）
     * -- GETTER --
     * 获取采集队列（用于消费者消费）
     *
     * @return 采集队列
     */
    @Getter
    private final BlockingQueue<RawLogEvent> logQueue;

    /**
     * 读取线程池
     */
    private ExecutorService readExecutor;

    /**
     * 文件监听服务
     */
    private ExecutorService watcherExecutor;

    /**
     * 检查点保存线程
     */
    private ScheduledExecutorService checkpointScheduler;

    /**
     * 日志存储服务
     */
    private final RawLogEventService rawLogEventService;

    /**
     * 存储消费者线程
     */
    private ExecutorService consumerExecutor;

    /**
     * 文件监听服务
     */
    private WatchService watchService;

    /**
     * RabbitTemplate 用于发送消息到脱敏队列
     */
    private RabbitTemplate rabbitTemplate;

    /**
     * 多文件采集支持
     * 存储文件路径 -> 文件上下文信息的映射
     */
    private final Map<String, FileContext> fileContextMap = new ConcurrentHashMap<>();

    /**
     * 采集的文件模式（如果有）
     */
    private String[] filePatterns;

    /**
     * 是否是目录模式（path是目录且有filePattern）
     */
    private boolean isDirectoryMode;

    /**
     * 目录扫描间隔（毫秒）
     */
    private static final long DIRECTORY_SCAN_INTERVAL_MS = 5000;

    /**
     * 单个文件上下文
     */
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

    /**
     * 构造函数
     *
     * @param logSource          日志源配置
     * @param checkpointManager  检查点管理器
     * @param config             采集配置
     * @param rawLogEventService 日志存储服务（已废弃，使用RabbitMQ发送消息）
     * @param rabbitTemplate     RabbitTemplate 用于发送消息到脱敏队列
     */
    public LocalFileCollector(LogSource logSource,
                              CheckpointManager checkpointManager,
                              CollectionConfig config,
                              RawLogEventService rawLogEventService,
                              RabbitTemplate rabbitTemplate) {
        this.id = UUID.randomUUID().toString();
        this.logSource = logSource;
        this.checkpointManager = checkpointManager;
        this.config = config;
        this.rawLogEventService = rawLogEventService;
        this.rabbitTemplate = rabbitTemplate;
        this.logQueue = new LinkedBlockingQueue<>(config.getQueueCapacity());
    }

    /**
     * 获取要采集的文件路径列表
     * 优先使用 paths 字段（JSON格式），兼容旧的 path 字段
     */
    private List<String> getFilePaths() {
        List<String> pathsList = logSource.getPathsList();
        if (pathsList != null && !pathsList.isEmpty()) {
            return pathsList;
        }
        // 兼容旧字段
        String legacyPath = logSource.getPath();
        if (legacyPath != null && !legacyPath.isEmpty()) {
            return Collections.singletonList(legacyPath);
        }
        return Collections.emptyList();
    }

    /**
     * 判断是否使用多路径模式
     */
    private boolean isMultiPathMode() {
        List<String> paths = getFilePaths();
        return paths != null && paths.size() > 1;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return logSource.getName();
    }

    @Override
    public CollectionState getState() {
        return state;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            state = CollectionState.STARTING;
            
            List<String> filePaths = getFilePaths();
            log.info("Starting local file collector: name={}, paths={}, multiPathMode={}",
                    getName(), filePaths, isMultiPathMode());

            try {
                // 1. 初始化线程池
                initExecutors();

                // 2. 获取文件路径列表
                if (filePaths.isEmpty()) {
                    throw new IllegalArgumentException("No file paths configured for collection");
                }

                // 3. 初始化多文件/目录模式
                initMultiPathMode(filePaths);

                // 4. 初始化所有文件
                initAllFiles();

                // 5. 启动文件监听
                startFileWatcher();

                // 6. 启动日志存储消费者（将日志存入数据库）
                startConsumer();

                // 7. 启动读取循环
                startReadLoop();

                // 8. 启动检查点定时保存
                startCheckpointScheduler();

                // 9. 启动目录扫描线程（如果需要）
                if (isDirectoryMode) {
                    startDirectoryScanner();
                }

                state = CollectionState.RUNNING;
                log.info("Local file collector started successfully: name={}", getName());

            } catch (Exception e) {
                log.error("Failed to start local file collector: name={}, error={}", getName(), e.getMessage(), e);
                state = CollectionState.ERROR;
                stop();
            }
        } else {
            log.warn("Collector is already running: name={}", getName());
        }
    }

    /**
     * 初始化多路径模式
     */
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

    /**
     * 初始化所有文件
     */
    private void initAllFiles() {
        List<String> filePaths = getFilePaths();
        
        if (isDirectoryMode) {
            // 目录模式：扫描目录下的文件
            scanAndInitFiles();
        } else {
            // 单文件或多路径模式：初始化所有指定的文件到 fileContextMap
            for (String filePath : filePaths) {
                if (!fileContextMap.containsKey(filePath)) {
                    FileContext ctx = new FileContext(filePath);
                    fileContextMap.put(filePath, ctx);
                    loadCheckpointForFile(filePath);
                    try {
                        openFileForContext(ctx);
                        log.info("Initialized file for collection: {}", filePath);
                    } catch (IOException e) {
                        log.error("Failed to open file: {}", filePath, e);
                    }
                }
            }
            
            // 设置监控目录为第一个文件的父目录
            if (!filePaths.isEmpty()) {
                watchPath = Paths.get(filePaths.get(0)).getParent();
            }
        }
    }

    /**
     * 扫描目录并初始化匹配的文件
     */
    private void scanAndInitFiles() {
        List<String> filePaths = getFilePaths();
        String dirPath = filePaths.isEmpty() ? logSource.getPath() : filePaths.get(0);
        
        // 如果只有一个路径且是目录，使用该目录
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

                        // 加载该文件的检查点
                        loadCheckpointForFile(fullPath);

                        // 打开文件
                        openFileForContext(ctx);

                        log.info("Initialized file for collection: {}", fullPath);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan directory: {}", dirPath, e);
        }
    }

    /**
     * 检查文件名是否匹配任意一个模式
     */
    private boolean matchesPattern(String fileName) {
        if (filePatterns == null || filePatterns.length == 0) {
            return false;
        }

        for (String pattern : filePatterns) {
            // 支持通配符 * 
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

    /**
     * 启动目录扫描线程
     */
    private void startDirectoryScanner() {
        watcherExecutor.submit(() -> {
            log.info("Directory scanner started for: {}", logSource.getPath());
            while (running.get()) {
                try {
                    Thread.sleep(DIRECTORY_SCAN_INTERVAL_MS);

                    // 检查是否有新文件
                    scanAndInitFiles();

                    // 检查文件是否被删除
                    checkDeletedFiles();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in directory scanner", e);
                }
            }
            log.info("Directory scanner stopped for: {}", logSource.getPath());
        });
    }

    /**
     * 检查已删除的文件
     */
    private void checkDeletedFiles() {
        Set<String> toRemove = new HashSet<>();

        for (Map.Entry<String, FileContext> entry : fileContextMap.entrySet()) {
            String path = entry.getKey();
            if (!new File(path).exists()) {
                log.info("File deleted, removing from collection: {}", path);
                try {
                    FileContext ctx = entry.getValue();
                    if (ctx.file != null) {
                        ctx.file.close();
                    }
                } catch (IOException e) {
                    log.warn("Error closing file: {}", path, e);
                }
                toRemove.add(path);
            }
        }

        fileContextMap.keySet().removeAll(toRemove);
    }

    /**
     * 为指定文件加载检查点
     */
    private void loadCheckpointForFile(String filePath) {
        CollectionCheckpoint checkpoint = checkpointManager.load(logSource.getId().toString(), filePath);
        FileContext ctx = fileContextMap.get(filePath);
        if (ctx != null && checkpoint != null) {
            ctx.filePointer = checkpoint.getOffset();
            log.info("Loaded checkpoint for file: path={}, offset={}", filePath, ctx.filePointer);
        }
    }

    /**
     * 为FileContext打开文件
     */
    private void openFileForContext(FileContext ctx) throws IOException {
        Path path = Paths.get(ctx.filePath);
        File fileObj = path.toFile();

        if (!fileObj.exists()) {
            log.warn("Log file not found: {}", ctx.filePath);
            return;
        }

        // 更新文件信息
        updateFileInfoForContext(ctx, path);

        // 打开文件
        ctx.file = new RandomAccessFile(fileObj, "r");
        ctx.file.seek(ctx.filePointer);

        log.info("Opened log file: path={}, offset={}, size={}",
                ctx.filePath, ctx.filePointer, ctx.currentFileSize);

        ctx.active.set(true);
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            state = CollectionState.STOPPING;
            log.info("Stopping local file collector: name={}", getName());

            try {
                // 1. 保存最终检查点
                saveCheckpointSync();

                // 2. 关闭线程池
                shutdownExecutors();

                // 3. 关闭文件
                closeFile();

                // 4. 关闭WatchService
                closeWatchService();

            } catch (Exception e) {
                log.error("Error while stopping collector: name={}", getName(), e);
            }

            state = CollectionState.STOPPED;
            log.info("Local file collector stopped: name={}, totalLines={}", getName(), collectedLines.get());

        } else {
            log.warn("Collector is not running: name={}", getName());
        }
    }

    @Override
    public void pause() {
        if (running.get() && paused.compareAndSet(false, true)) {
            state = CollectionState.PAUSED;
            log.info("Collector paused: name={}", getName());
        }
    }

    @Override
    public void resume() {
        if (running.get() && paused.compareAndSet(true, false)) {
            state = CollectionState.RUNNING;
            log.info("Collector resumed: name={}", getName());
        }
    }

    @Override
    public boolean isRunning() {
        return running.get() && state == CollectionState.RUNNING;
    }

    @Override
    public long getCollectedLines() {
        return collectedLines.get();
    }

    @Override
    public boolean isHealthy() {
        return state == CollectionState.RUNNING && !paused.get();
    }

    /**
     * 初始化线程池
     */
    private void initExecutors() {
        readExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "local-file-reader-" + getName());
            t.setDaemon(true);
            return t;
        });

        watcherExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "local-file-watcher-" + getName());
            t.setDaemon(true);
            return t;
        });

        checkpointScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "checkpoint-scheduler-" + getName());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 加载检查点
     */
    private void loadCheckpoint() {
        CollectionCheckpoint checkpoint = checkpointManager.load(
                logSource.getId().toString(),
                logSource.getPath()
        );
        filePointer = checkpoint.getOffset() != null ? checkpoint.getOffset() : 0L;
        currentInode = checkpoint.getFileInode();
        log.info("Loaded checkpoint: name={}, path={}, offset={}, inode={}",
                getName(), logSource.getPath(), filePointer, currentInode);
    }

    /**
     * 打开文件
     */
    private void openFile() throws IOException {
        Path filePath = Paths.get(logSource.getPath());
        watchPath = filePath.getParent();

        File fileObj = filePath.toFile();
        if (!fileObj.exists()) {
            log.warn("Log file not found: path={}, will wait for file creation", logSource.getPath());
            return;
        }

        // 获取文件信息
        updateFileInfo(filePath);

        // 打开文件
        file = new RandomAccessFile(fileObj, "r");
        file.seek(filePointer);

        log.info("Opened log file: path={}, offset={}, size={}",
                logSource.getPath(), filePointer, currentFileSize);

        // 初始化日志格式检测模式
        initLogStartPattern();
    }

    /**
     * 初始化日志开始行检测模式
     */
    private void initLogStartPattern() {
        LogFormat logFormat = logSource.getLogFormat();
        if (logFormat == null) {
            // 默认使用 Spring Boot 格式检测
            logFormat = LogFormat.SPRING_BOOT;
        }

        String patternStr = getLogStartPattern(logFormat, logSource.getCustomPattern());
        if (patternStr != null) {
            this.logStartPattern = Pattern.compile(patternStr);
        }
    }

    /**
     * 获取日志开始行的正则表达式
     */
    private String getLogStartPattern(LogFormat format, String customPattern) {
        return switch (format) {
            case SPRING_BOOT ->
                // Spring Boot 日志通常以日期时间开头: 2026-03-10 15:11:48.301
                // 使用显式空格匹配，避免 \s 的问题
                    "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?";
            case LOG4J ->
                // Log4j 日志: 2026-03-10 11:37:02
                    "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}";
            case NGINX, NGINX_ACCESS, NGINX_ERROR ->
                // Nginx 日志（Access 或 Error）
                // Access: 127.0.0.1 - -
                // Error: 2026/03/13 14:26:31
                    "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\s+-\\s+-|^\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}";
            case JSON ->
                // JSON 日志以 { 开头
                    "^\\{";
            case CUSTOM ->
                // 自定义正则
                    customPattern;
            default ->
                // 普通文本不做多行合并
                    null;
        };
    }

    /**
     * 检测是否为日志开始行
     */
    private boolean isLogStart(String line) {
        if (logStartPattern == null) {
            // 如果没有配置模式，默认都是新日志行
            return true;
        }
        return logStartPattern.matcher(line).find(0);
    }

    /**
     * 更新文件信息
     */
    private void updateFileInfo(Path filePath) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            currentFileSize = attrs.size();
            currentFileMtime = attrs.lastModifiedTime().toMillis();

            // 获取inode（POSIX属性）
            try {
                DosFileAttributes dosAttrs = Files.readAttributes(filePath, DosFileAttributes.class);
                currentInode = String.valueOf(dosAttrs.fileKey());
            } catch (Exception e) {
                // 如果无法获取inode，使用文件路径+大小+修改时间作为唯一标识
                currentInode = filePath.toString() + "_" + currentFileSize + "_" + currentFileMtime;
            }

        } catch (IOException e) {
            log.warn("Failed to read file attributes: path={}", filePath, e);
        }
    }

    /**
     * 为FileContext更新文件信息
     */
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

    /**
     * 关闭文件
     */
    private void closeFile() {
        if (file != null) {
            try {
                // 保存最终检查点
                saveCheckpointSync();
                file.close();
                log.info("Closed log file: path={}", logSource.getPath());
            } catch (IOException e) {
                log.warn("Error closing file: path={}", logSource.getPath(), e);
            } finally {
                file = null;
            }
        }
    }

    /**
     * 启动文件监听
     */
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

        // 监听创建、修改、删除事件
        watchPath.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

        watcherExecutor.submit(this::watchLoop);

        log.info("Started file watcher: path={}", watchPath);
    }

    /**
     * 关闭WatchService
     */
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

    /**
     * 文件监听循环
     */
    private void watchLoop() {
        log.info("File watcher loop started: path={}", watchPath);

        while (running.get()) {
            try {
                WatchKey key = watchService.poll(config.getFileRotateCheckIntervalMs(), TimeUnit.MILLISECONDS);

                if (key == null) {
                    // 超时，检查文件是否被轮转
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

                    // 检查是否是当前监控的文件
                    if (changedPath.getFileName().toString().equals(
                            Paths.get(logSource.getPath()).getFileName().toString())) {


                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            // 文件被创建（可能是轮转后的新文件）
                            handleFileCreated(changedPath);
                        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            // 文件被修改
                            handleFileModified(changedPath);
                        }
                    }
                }

                key.reset();

                // 检查文件轮转
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

    /**
     * 处理文件创建事件
     */
    private void handleFileCreated(Path changedPath) {
        log.info("New file detected (possible rotation): path={}", changedPath);

        // 检查是否是轮转
        checkFileRotation();
    }

    /**
     * 处理文件修改事件
     */
    private void handleFileModified(Path changedPath) {

        // 检查文件是否被轮转
        Path currentPath = Paths.get(logSource.getPath());
        if (!currentPath.toFile().exists()) {
            log.info("Original file deleted, treating as rotation");
            checkFileRotation();
        }
    }

    /**
     * 检查文件轮转
     * 注意：只有当文件大小变小时才认为是轮转，这是最可靠的标志
     */
    private void checkFileRotation() {
        Path filePath = Paths.get(logSource.getPath());

        if (!filePath.toFile().exists()) {
            return;
        }

        try {
            // 检查文件大小是否缩小（轮转的标志）
            long newSize = Files.size(filePath);

            // 只有当文件大小变小时才认为是轮转
            if (newSize < filePointer) {
                log.info("File rotation detected: path={}, oldSize={}, newSize={}",
                        filePath, filePointer, newSize);

                handleFileRotation(filePath);
            }

            // 更新当前文件信息
            updateFileInfo(filePath);

        } catch (IOException e) {
            log.warn("Failed to check file rotation: path={}", filePath, e);
        }
    }

    /**
     * 处理文件轮转
     */
    private void handleFileRotation(Path newFilePath) {
        try {
            // 关闭旧文件
            closeFile();

            // 更新文件信息
            updateFileInfo(newFilePath);

            // 重置指针
            filePointer = 0L;

            // 打开新文件
            openFile();

            log.info("Handled file rotation: oldInode={}, newInode={}",
                    currentInode, newFilePath.toString());

        } catch (Exception e) {
            log.error("Failed to handle file rotation", e);
            state = CollectionState.ERROR;
        }
    }

    /**
     * 启动读取循环
     */
    private void startReadLoop() {
        readExecutor.submit(this::readLoop);
    }

    /**
     * 读取循环
     */
    private void readLoop() {
        List<String> filePaths = getFilePaths();
        log.info("Read loop started: paths={}, directoryMode={}", filePaths, isDirectoryMode);

        if (isDirectoryMode) {
            readLoopDirectoryMode();
        } else {
            readLoopSingleFileMode();
        }

        log.info("Read loop stopped: paths={}", filePaths);
    }

    /**
     * 单文件/多路径模式读取循环 - 使用 fileContextMap
     */
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

                List<String> pathsToRemove = new ArrayList<>();

                for (Map.Entry<String, FileContext> entry : fileContextMap.entrySet()) {
                    String filePath = entry.getKey();
                    FileContext ctx = entry.getValue();

                    if (!ctx.active.get() || ctx.file == null) {
                        continue;
                    }

                    try {
                        readFromFileContext(ctx, filePath);
                    } catch (Exception e) {
                        log.error("Error reading from file: {}", filePath, e);
                    }
                }

                if (linesSinceCheckpoint >= config.getCheckpointInterval()
                        || System.currentTimeMillis() - lastCheckpointTime >= config.getCheckpointIntervalMs()) {
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

    /**
     * 保存所有文件的检查点
     */
    private void saveAllCheckpointsAsync() {
        for (Map.Entry<String, FileContext> entry : fileContextMap.entrySet()) {
            String filePath = entry.getKey();
            FileContext ctx = entry.getValue();
            if (ctx.active.get()) {
                saveCheckpointForFileAsync(filePath, ctx);
            }
        }
    }

    /**
     * 目录模式读取循环 - 支持多文件并发采集
     */
    private void readLoopDirectoryMode() {
        while (running.get()) {
            try {
                while (paused.get() && running.get()) {
                    Thread.sleep(100);
                }

                if (!running.get()) {
                    break;
                }

                // 检查是否有活跃的文件需要读取
                if (fileContextMap.isEmpty()) {
                    Thread.sleep(100);
                    continue;
                }

                // 遍历所有文件上下文进行读取
                List<String> pathsToRemove = new ArrayList<>();

                for (Map.Entry<String, FileContext> entry : fileContextMap.entrySet()) {
                    String filePath = entry.getKey();
                    FileContext ctx = entry.getValue();

                    if (!ctx.active.get() || ctx.file == null) {
                        continue;
                    }

                    try {
                        readFromFileContext(ctx, filePath);
                    } catch (Exception e) {
                        log.error("Error reading from file: {}", filePath, e);
                        ctx.active.set(false);
                    }
                }

                // 短暂休眠避免CPU占用过高
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

    /**
     * 从文件上下文读取数据
     */
    private void readFromFileContext(FileContext ctx, String filePath) throws IOException {
        // 检查文件是否存在且大小变化
        File fileObj = new File(filePath);
        if (!fileObj.exists()) {
            log.warn("File no longer exists: {}", filePath);
            ctx.active.set(false);
            return;
        }

        long currentSize = fileObj.length();
        if (currentSize < ctx.currentFileSize) {
            // 文件被轮转了，重置位置
            log.info("File rotation detected: {} (old size: {}, new size: {})",
                    filePath, ctx.currentFileSize, currentSize);
            ctx.filePointer = 0;
            ctx.resetBuffer();
        }

        // 更新文件大小
        ctx.currentFileSize = currentSize;

        // 如果没有新数据，跳过
        if (ctx.filePointer >= ctx.currentFileSize) {
            return;
        }

        // 移动到当前位置
        ctx.file.seek(ctx.filePointer);

        // 读取数据
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

        ctx.filePointer += bytesRead;

        String content = new String(readBuffer, logSource.getEncoding());
        lineBuilder.append(content);

        int lineIndex;
        while ((lineIndex = lineBuilder.indexOf("\n")) >= 0) {
            String line = lineBuilder.substring(0, lineIndex);
            lineBuilder.delete(0, lineIndex + 1);

            if (!line.isEmpty()) {
                long currentLineNumber = ++ctx.collectedLines;

                // 处理多行日志（为每个文件独立处理）
                processMultiLineLogForContext(ctx, line, currentLineNumber, filePath);

                linesSinceCheckpoint++;

                // 定期保存检查点
                if (linesSinceCheckpoint >= config.getCheckpointInterval()
                        || System.currentTimeMillis() - lastCheckpointTime >= config.getCheckpointIntervalMs()) {
                    saveCheckpointForFileAsync(filePath, ctx);
                    linesSinceCheckpoint = 0;
                    lastCheckpointTime = System.currentTimeMillis();
                }
            }
        }
    }

    /**
     * 为指定文件保存检查点
     */
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

    /**
     * 为文件上下文处理多行日志
     */
    private void processMultiLineLogForContext(FileContext ctx, String line, long lineNumber, String filePath) {
        // 检查是否是日志开始行
        if (logStartPattern != null) {
            Matcher matcher = logStartPattern.matcher(line);
            if (matcher.find()) {
                // 如果缓冲区有内容，说明是多行日志的结束，发送到队列
                if (ctx.multiLineBuffer.length() > 0) {
                    sendToQueue(ctx.multiLineBuffer.toString(), ctx.multiLineStartLineNumber, filePath);
                    ctx.resetBuffer();
                }
                // 开始新的多行日志
                ctx.multiLineBuffer.append(line);
                ctx.multiLineStartLineNumber = lineNumber;
                return;
            }
        }

        // 继续追加到多行缓冲区
        if (ctx.multiLineBuffer.length() > 0) {
            ctx.multiLineBuffer.append("\n").append(line);
        } else {
            // 没有开始的多行日志，直接发送
            sendToQueue(line, lineNumber, filePath);
        }

        // 如果缓冲区太大，也发送到队列（防止内存溢出）
        if (ctx.multiLineBuffer.length() > 65536) {
            sendToQueue(ctx.multiLineBuffer.toString(), ctx.multiLineStartLineNumber, filePath);
            ctx.resetBuffer();
        }
    }

    /**
     * 发送日志到队列
     */
    private void sendToQueue(String content, long lineNumber, String filePath) {
        try {
            RawLogEvent event = new RawLogEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setSourceId(logSource.getId());
            event.setSourceName(logSource.getName());
            event.setFilePath(filePath);
            event.setRawContent(content);
            event.setLineNumber(lineNumber);
            event.setFileOffset(filePointer);
            event.setByteLength(content.getBytes().length);
            event.setCollectionTime(java.time.LocalDateTime.now());
            event.setLogFormat(logSource.getLogFormat() != null ? logSource.getLogFormat().name() : null);
            event.setLogFormatPattern(logSource.getLogFormatPattern());

            // #region agent log
            Map<String, Object> agentData = new HashMap<>();
            agentData.put("sourceId", logSource.getId() != null ? logSource.getId().toString() : null);
            agentData.put("logFormat", logSource.getLogFormat() != null ? logSource.getLogFormat().name() : null);
            agentData.put("patternNull", logSource.getLogFormatPattern() == null);
            agentData.put("patternLen", logSource.getLogFormatPattern() == null ? 0 : logSource.getLogFormatPattern().length());
            agentData.put("patternTail", logSource.getLogFormatPattern() == null ? null : logSource.getLogFormatPattern().substring(Math.max(0, logSource.getLogFormatPattern().length() - 80)));
            agentData.put("filePath", filePath);
            agentData.put("lineNumber", lineNumber);
            agentLog(
                    "post-fix",
                    "H2",
                    "LocalFileCollector.java:sendToQueue:pattern_snapshot",
                    "Collector snapshot of LogSource logFormatPattern",
                    agentData
            );
            // #endregion

            if (!logQueue.offer(event, 5, TimeUnit.SECONDS)) {
                log.warn("Failed to offer log event to queue, queue is full");
            }
        } catch (Exception e) {
            log.error("Failed to send log to queue", e);
        }
    }

    /**
     * 处理多行日志
     * 注意：此方法不负责行号递增，由调用者统一管理
     *
     * @param line       当前读取的行
     * @param lineNumber 当前行的物理行号
     */
    private void processMultiLineLog(String line, long lineNumber) {
        if (!running.get()) {
            return;
        }

        boolean isLogStart = isLogStart(line);

        if (isLogStart) {
            // 如果是新的日志开始，先处理缓冲区中的内容（之前的完整多行日志）
            if (!multiLineBuffer.isEmpty()) {
                flushMultiLineBuffer();
            }
            // 记录新的日志开始行号
            multiLineStartLineNumber = lineNumber;
            // 将新日志开始行也加入缓冲区，等待后续行合并
            multiLineBuffer.append(line);
        } else {
            // 如果不是新日志开始，则追加到缓冲区
            if (!multiLineBuffer.isEmpty()) {
                multiLineBuffer.append("\n");
            }
            multiLineBuffer.append(line);
        }
    }

    /**
     * 刷新多行日志缓冲区
     */
    private void flushMultiLineBuffer() {
        if (!running.get()) {
            return;
        }
        if (!multiLineBuffer.isEmpty()) {
            String logContent = multiLineBuffer.toString();
            processLine(logContent, multiLineStartLineNumber);
            multiLineBuffer.setLength(0);
            multiLineStartLineNumber = 0;
        }
    }

    /**
     * 处理一行日志（指定行号）
     */
    private void processLine(String line, long lineNumber) {
        try {
            // 检查是否已停止，如果是则不处理
            if (!running.get()) {
                return;
            }

            RawLogEvent event = RawLogEvent.create(
                    logSource.getId(),
                    logSource.getName(),
                    logSource.getPath(),
                    line,
                    lineNumber,
                    filePointer,
                    line.getBytes(logSource.getEncoding()).length,
                    currentInode,
                    java.time.LocalDateTime.now(),
                    logSource.getLogFormat() != null ? logSource.getLogFormat().name() : null,
                    logSource.getLogFormatPattern()
            );

            // #region agent log
            Map<String, Object> agentData2 = new HashMap<>();
            agentData2.put("sourceId", logSource.getId() != null ? logSource.getId().toString() : null);
            agentData2.put("logFormat", logSource.getLogFormat() != null ? logSource.getLogFormat().name() : null);
            agentData2.put("patternNull", logSource.getLogFormatPattern() == null);
            agentData2.put("patternLen", logSource.getLogFormatPattern() == null ? 0 : logSource.getLogFormatPattern().length());
            agentData2.put("patternTail", logSource.getLogFormatPattern() == null ? null : logSource.getLogFormatPattern().substring(Math.max(0, logSource.getLogFormatPattern().length() - 80)));
            agentData2.put("filePath", logSource.getPath());
            agentData2.put("lineNumber", lineNumber);
            agentLog(
                    "post-fix",
                    "H2",
                    "LocalFileCollector.java:processLine:pattern_snapshot",
                    "Collector snapshot (RawLogEvent.create) of LogSource logFormatPattern",
                    agentData2
            );
            // #endregion

            // 放入队列
            boolean offered = logQueue.offer(event, 1, TimeUnit.SECONDS);

            if (!offered) {
                log.warn("Log queue is full, line dropped: lineNumber={}, queueSize={}", event.getLineNumber(), logQueue.size());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Collector interrupted, stopping: name={}", getName());
        } catch (Exception e) {
            if (running.get()) {
                log.error("Failed to process line: content={}", line, e);
            }
        }
    }

    // #region agent log
    private static final Path AGENT_DEBUG_LOG_PATH = Path.of("/Users/cityseason/Documents/graduation_project/project/.cursor/debug-d4a73b.log");

    private static void agentLog(String runId, String hypothesisId, String location, String message, Map<String, Object> data) {
        try {
            long ts = System.currentTimeMillis();
            StringBuilder sb = new StringBuilder(256);
            sb.append('{')
                    .append("\"sessionId\":\"d4a73b\",")
                    .append("\"runId\":\"").append(escapeJson(runId)).append("\",")
                    .append("\"hypothesisId\":\"").append(escapeJson(hypothesisId)).append("\",")
                    .append("\"location\":\"").append(escapeJson(location)).append("\",")
                    .append("\"message\":\"").append(escapeJson(message)).append("\",")
                    .append("\"timestamp\":").append(ts).append(',')
                    .append("\"data\":").append(toJsonObject(data))
                    .append('}')
                    .append('\n');
            Files.writeString(
                    AGENT_DEBUG_LOG_PATH,
                    sb.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toJsonObject(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("\"").append(escapeJson(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v.toString());
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(v))).append("\"");
            }
        }
        sb.append('}');
        return sb.toString();
    }
    // #endregion

    /**
     * 启动检查点定时保存
     */
    private void startCheckpointScheduler() {
        checkpointScheduler.scheduleAtFixedRate(
                this::saveCheckpointAsync,
                config.getCheckpointIntervalMs(),
                config.getCheckpointIntervalMs(),
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 异步保存检查点
     */
    private void saveCheckpointAsync() {
        if (!running.get()) {
            return;
        }

        try {
            checkpointManager.save(
                    logSource.getId().toString(),
                    logSource.getPath(),
                    filePointer,
                    currentFileSize,
                    currentInode,
                    java.time.LocalDateTime.now()
            );
        } catch (Exception e) {
            log.warn("Failed to save checkpoint asynchronously", e);
        }
    }

    /**
     * 同步保存检查点（用于停止时）
     */
    private void saveCheckpointSync() {
        try {
            checkpointManager.save(
                    logSource.getId().toString(),
                    logSource.getPath(),
                    filePointer,
                    currentFileSize,
                    currentInode,
                    java.time.LocalDateTime.now()
            );
            log.info("Checkpoint saved synchronously: path={}, offset={}",
                    logSource.getPath(), filePointer);
        } catch (Exception e) {
            log.error("Failed to save checkpoint synchronously", e);
        }
    }

    /**
     * 关闭线程池
     */
    private void shutdownExecutors() {
        if (readExecutor != null) {
            readExecutor.shutdownNow();
            try {
                if (!readExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Read executor did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (watcherExecutor != null) {
            watcherExecutor.shutdownNow();
        }

        if (consumerExecutor != null) {
            consumerExecutor.shutdownNow();
            try {
                if (!consumerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Consumer executor did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (checkpointScheduler != null) {
            checkpointScheduler.shutdownNow();
            try {
                if (!checkpointScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Checkpoint scheduler did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 启动日志存储消费者
     * <p>
     * 消费者线程从队列中取出日志事件，并保存到数据库
     */
    private void startConsumer() {
        consumerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "log-consumer-" + getName());
            t.setDaemon(true);
            return t;
        });

        consumerExecutor.submit(this::consumeLoop);

        log.info("Started log consumer: name={}", getName());
    }

    /**
     * 消费循环
     * <p>
     * 从队列中取出日志事件，发送到 RabbitMQ 队列进行脱敏处理
     */
    private void consumeLoop() {
        log.info("Consumer loop started: name={}", getName());

        // 批量处理缓冲区
        List<RawLogEvent> batchBuffer = new ArrayList<>();
        long lastFlushTime = System.currentTimeMillis();
        int batchSize = 100; // 批量发送大小
        long flushIntervalMs = 5000; // 5秒强制刷新间隔

        log.info("Consumer config: batchSize={}, flushIntervalMs={}, queueCapacity={}",
                batchSize, flushIntervalMs, logQueue.size());

        while (running.get() || !logQueue.isEmpty()) {
            try {
                // 从队列中取出日志（带超时）
                RawLogEvent event = logQueue.poll(1, TimeUnit.SECONDS);

                if (event != null) {
                    batchBuffer.add(event);

                    // 如果达到批量大小，发送到 RabbitMQ
                    if (batchBuffer.size() >= batchSize) {
                        sendToRabbitMQ(batchBuffer);
                        batchBuffer.clear();
                        lastFlushTime = System.currentTimeMillis();
                    }
                }

                // 检查是否需要强制刷新
                if (!batchBuffer.isEmpty() &&
                        System.currentTimeMillis() - lastFlushTime >= flushIntervalMs) {
                    sendToRabbitMQ(batchBuffer);
                    batchBuffer.clear();
                    lastFlushTime = System.currentTimeMillis();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in consumer loop", e);
            }
        }

        // 最后刷新一次缓冲区
        if (!batchBuffer.isEmpty()) {
            sendToRabbitMQ(batchBuffer);
        }

        log.info("Consumer loop stopped: name={}", getName());
    }

    /**
     * 发送消息到 RabbitMQ 队列
     *
     * @param events 日志事件列表
     */
    private void sendToRabbitMQ(List<RawLogEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        try {
            for (RawLogEvent event : events) {
                // 构建脱敏消息
                LogDesensitizationMessage message = LogDesensitizationMessage.builder()
                        .messageId(UUID.randomUUID())
                        .sourceId(event.getSourceId())
                        .sourceName(event.getSourceName())
                        .filePath(event.getFilePath())
                        .rawContent(event.getRawContent())
                        .lineNumber(event.getLineNumber())
                        .offset(event.getFileOffset())
                        .collectionTime(event.getCollectionTime())
                        .logFormat(logSource.getLogFormat() != null ? logSource.getLogFormat().name() : null)
                        .logFormatPattern(logSource.getLogFormatPattern())
                        .customPattern(logSource.getCustomPattern())
                        .desensitizationConfig(buildDesensitizationConfig())
                        .build();

                // 发送到 RabbitMQ
                String routingKey = "log.raw." + event.getSourceId();
                rabbitTemplate.convertAndSend(RabbitMQConfig.LOG_EXCHANGE, routingKey, message);
            }
        } catch (Exception e) {
            log.error("Failed to send messages to RabbitMQ", e);
            throw e;
        }
    }

    /**
     * 构建脱敏配置
     */
    private LogDesensitizationMessage.DesensitizationConfig buildDesensitizationConfig() {
        return LogDesensitizationMessage.DesensitizationConfig.builder()
                .enabled(logSource.getDesensitizationEnabled())
                .enabledRuleIds(logSource.getEnabledRuleIds())
                .customRules(buildCustomRules())
                .build();
    }

    /**
     * 构建自定义规则
     */
    private List<LogDesensitizationMessage.DesensitizationConfig.CustomRule> buildCustomRules() {
        if (logSource.getCustomRules() == null) {
            return null;
        }

        return logSource.getCustomRules().stream()
                .map(rule -> LogDesensitizationMessage.DesensitizationConfig.CustomRule.builder()
                        .id(rule.getId())
                        .name(rule.getName())
                        .pattern(rule.getPattern())
                        .maskType(rule.getMaskType())
                        .replacement(rule.getReplacement())
                        .build())
                .toList();
    }

    /**
     * 获取日志源类型
     */
    public LogSourceType getSourceType() {
        return LogSourceType.LOCAL_FILE;
    }

}
