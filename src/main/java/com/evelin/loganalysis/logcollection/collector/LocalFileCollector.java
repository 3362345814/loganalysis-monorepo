package com.evelin.loganalysis.logcollection.collector;

import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.config.RabbitMQConfig;
import com.evelin.loganalysis.logcollection.dto.LogDesensitizationMessage;
import com.evelin.loganalysis.logcollection.model.CollectionCheckpoint;
import com.evelin.loganalysis.logcollection.model.CollectionState;
import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logcollection.service.CheckpointManager;
import com.evelin.loganalysis.logcollection.service.RawLogEventService;
import com.evelin.loganalysis.logcommon.enums.LogSourceType;
import com.evelin.loganalysis.logcommon.model.LogSource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
     * 构造函数
     *
     * @param logSource          日志源配置
     * @param checkpointManager  检查点管理器
     * @param config             采集配置
     * @param rawLogEventService 日志存储服务（已废弃，使用RabbitMQ发送消息）
     * @param rabbitTemplate    RabbitTemplate 用于发送消息到脱敏队列
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
            log.info("Starting local file collector: name={}, path={}", getName(), logSource.getPath());

            try {
                // 1. 初始化线程池
                initExecutors();

                // 2. 加载检查点
                loadCheckpoint();

                // 3. 打开文件
                openFile();

                // 4. 启动文件监听
                startFileWatcher();

                // 5. 启动日志存储消费者（将日志存入数据库）
                startConsumer();

                // 6. 启动读取循环
                startReadLoop();

                // 7. 启动检查点定时保存
                startCheckpointScheduler();

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

                        log.debug("File change detected: kind={}, path={}", kind, changedPath);

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
        log.debug("File modified: path={}", changedPath);

        // 检查文件是否被轮转
        Path currentPath = Paths.get(logSource.getPath());
        if (!currentPath.toFile().exists()) {
            log.info("Original file deleted, treating as rotation");
            checkFileRotation();
        }
    }

    /**
     * 检查文件轮转
     */
    private void checkFileRotation() {
        Path filePath = Paths.get(logSource.getPath());

        if (!filePath.toFile().exists()) {
            return;
        }

        try {
            // 检查文件大小是否缩小（轮转的标志）
            long newSize = Files.size(filePath);
            long newMtime = Files.getLastModifiedTime(filePath).toMillis();

            // 如果新文件大小小于当前指针位置，说明文件被轮转
            if (newSize < filePointer || (newMtime > currentFileMtime && newSize == filePointer)) {
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
        log.info("Read loop started: path={}", logSource.getPath());

        ByteBuffer buffer = ByteBuffer.allocate(config.getReadBufferSize());
        StringBuilder lineBuilder = new StringBuilder();
        long lastCheckpointTime = System.currentTimeMillis();
        long linesSinceCheckpoint = 0;

        while (running.get()) {
            try {
                // 如果暂停，等待
                while (paused.get() && running.get()) {
                    Thread.sleep(100);
                }

                if (!running.get()) {
                    break;
                }

                // 读取数据
                buffer.clear();
                byte[] readBuffer = new byte[buffer.capacity()];
                int bytesRead = file.read(readBuffer, 0, readBuffer.length);

                if (bytesRead == -1) {
                    // 文件到达末尾，等待新内容
                    Thread.sleep(100);
                    continue;
                }

                // 如果读取的字节数小于缓冲区大小，只处理实际读取的部分
                if (bytesRead < readBuffer.length) {
                    byte[] trimmedBuffer = new byte[bytesRead];
                    System.arraycopy(readBuffer, 0, trimmedBuffer, 0, bytesRead);
                    readBuffer = trimmedBuffer;
                }

                // 按行分割
                String content = new String(readBuffer, logSource.getEncoding());
                lineBuilder.append(content);

                int lineIndex;
                while ((lineIndex = lineBuilder.indexOf("\n")) >= 0) {
                    String line = lineBuilder.substring(0, lineIndex);
                    lineBuilder.delete(0, lineIndex + 1);

                    if (!line.isEmpty()) {
                        // 处理行
                        processLine(line);

                        linesSinceCheckpoint++;
                        filePointer += line.getBytes().length + 1; // +1 for newline

                        // 定期保存检查点
                        if (linesSinceCheckpoint >= config.getCheckpointInterval()
                                || System.currentTimeMillis() - lastCheckpointTime >= config.getCheckpointIntervalMs()) {
                            saveCheckpointAsync();
                            linesSinceCheckpoint = 0;
                            lastCheckpointTime = System.currentTimeMillis();
                        }
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in read loop", e);
                state = CollectionState.ERROR;
                break;
            }
        }

        log.info("Read loop stopped: path={}", logSource.getPath());
    }

    /**
     * 处理一行日志
     */
    private void processLine(String line) {
        try {
            RawLogEvent event = RawLogEvent.create(
                    logSource.getId(),
                    logSource.getName(),
                    logSource.getPath(),
                    line,
                    collectedLines.incrementAndGet(),
                    filePointer,
                    line.getBytes(logSource.getEncoding()).length,
                    currentInode,
                    java.time.LocalDateTime.now()
            );

            // 放入队列
            boolean offered = logQueue.offer(event, 1, TimeUnit.SECONDS);

            if (!offered) {
                log.warn("Log queue is full, line dropped: lineNumber={}", event.getLineNumber());
            }

        } catch (Exception e) {
            log.error("Failed to process line: content={}", line, e);
        }
    }

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
                        .desensitizationConfig(buildDesensitizationConfig())
                        .build();

                // 发送到 RabbitMQ
                String routingKey = "log.raw." + event.getSourceId();
                rabbitTemplate.convertAndSend(RabbitMQConfig.LOG_EXCHANGE, routingKey, message);
            }
            log.debug("Sent {} log messages to RabbitMQ", events.size());
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
