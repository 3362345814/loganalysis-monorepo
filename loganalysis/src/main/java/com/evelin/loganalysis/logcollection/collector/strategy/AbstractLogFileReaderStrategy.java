package com.evelin.loganalysis.logcollection.collector.strategy;

import com.evelin.loganalysis.logcollection.collector.LogCollector;
import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.config.RabbitMQConfig;
import com.evelin.loganalysis.logcollection.dto.LogDesensitizationMessage;
import com.evelin.loganalysis.logcollection.model.CollectionState;
import com.evelin.loganalysis.logcollection.model.LogSource;
import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logcollection.service.CheckpointManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 日志文件读取策略抽象基类。
 * <p>
 * 封装日志采集的公共逻辑，包括：
 * <ul>
 *   <li>线程管理（读取线程、消费线程、检查点调度线程）</li>
 *   <li>日志队列与批处理发送</li>
 *   <li>多行日志缓冲与合并</li>
 *   <li>日志格式识别（logStartPattern）</li>
 *   <li>字符集处理</li>
 *   <li>RabbitMQ 消息发送</li>
 * </ul>
 * <p>
 * 子类只需实现文件读取特有的逻辑：
 * {@link #doConnect()}, {@link #doDisconnect()}, {@link #doInitFiles()}, {@link #doReadFiles()}
 */
@Slf4j
public abstract class AbstractLogFileReaderStrategy implements LogCollector, LogFileReaderStrategy {

    // ==================== 核心依赖 ====================
    protected final String id;
    @Getter
    protected final LogSource logSource;
    protected final CheckpointManager checkpointManager;
    protected final CollectionConfig config;
    protected final RabbitTemplate rabbitTemplate;

    // ==================== 状态变量 ====================
    protected volatile CollectionState state = CollectionState.STOPPED;
    protected final AtomicBoolean running;
    protected final AtomicBoolean paused;
    protected final AtomicLong collectedLines;

    // ==================== 字符集与日志格式 ====================
    protected Charset charset;
    protected Pattern logStartPattern;

    // ==================== 多行日志缓冲 ====================
    private final StringBuilder multiLineBuffer = new StringBuilder();
    private long multiLineStartLineNumber = 0;

    // ==================== 队列与批处理 ====================
    @Getter
    protected final BlockingQueue<RawLogEvent> logQueue;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long DEFAULT_FLUSH_INTERVAL_MS = 5000;
    private static final long CONSUMER_DRAIN_TIMEOUT_SECONDS = 15;

    // ==================== 线程池 ====================
    protected ExecutorService readerExecutor;
    protected ExecutorService consumerExecutor;
    protected ScheduledExecutorService checkpointScheduler;

    // ==================== LineProcessor 回调 ====================
    /**
     * 文件读取策略实现注册此回调，将读取到的每一行（或多行合并后的日志）
     * 传递给基类统一处理（入队、RabbitMQ 发送、检查点保存）。
     */
    protected LineProcessor lineProcessor;

    // ==================== 构造函数 ====================

    protected AbstractLogFileReaderStrategy(LogSource logSource,
                                            CheckpointManager checkpointManager,
                                            CollectionConfig config,
                                            RabbitTemplate rabbitTemplate,
                                            AtomicBoolean running,
                                            AtomicBoolean paused,
                                            AtomicLong collectedLines) {
        this.logSource = Objects.requireNonNull(logSource, "logSource cannot be null");
        this.checkpointManager = Objects.requireNonNull(checkpointManager, "checkpointManager cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate cannot be null");
        this.running = running != null ? running : new AtomicBoolean(false);
        this.paused = paused != null ? paused : new AtomicBoolean(false);
        this.collectedLines = collectedLines != null ? collectedLines : new AtomicLong(0);

        this.id = logSource.getId().toString();
        int queueCapacity = config.getQueueCapacity() > 0 ? config.getQueueCapacity() : 10000;
        this.logQueue = new LinkedBlockingQueue<>(queueCapacity);

        initCharset();
        initLogStartPattern();
    }

    // ==================== 公共初始化逻辑 ====================

    private void initCharset() {
        String encoding = logSource.getEncoding();
        if (encoding == null || encoding.isEmpty()) {
            encoding = "UTF-8";
        }
        try {
            this.charset = Charset.forName(encoding);
        } catch (Exception e) {
            log.warn("Invalid charset: {}, using UTF-8", encoding);
            this.charset = StandardCharsets.UTF_8;
        }
    }

    private void initLogStartPattern() {
        String patternStr = getLogStartPatternStr();
        if (patternStr != null) {
            this.logStartPattern = Pattern.compile(patternStr);
        }
    }

    /**
     * 根据日志格式获取日志行起始匹配模式。
     * 子类可覆盖以提供自定义实现。
     */
    protected String getLogStartPatternStr() {
        if (logSource.getCustomPattern() != null && !logSource.getCustomPattern().isEmpty()) {
            return logSource.getCustomPattern();
        }
        if (logSource.getLogFormat() == null) {
            return null;
        }
        return switch (logSource.getLogFormat()) {
            case SPRING_BOOT -> "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?";
            case LOG4J -> "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}";
            case NGINX, NGINX_ACCESS, NGINX_ERROR -> null;
            case JSON -> "^\\{";
            case CUSTOM -> logSource.getCustomPattern();
            default -> null;
        };
    }

    protected boolean isLogStart(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        if (logStartPattern == null) {
            return true;
        }
        return logStartPattern.matcher(line).find(0);
    }

    // ==================== LogCollector 接口实现 ====================

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
    public boolean isRunning() {
        return running.get() && state == CollectionState.RUNNING;
    }

    @Override
    public long getCollectedLines() {
        return collectedLines.get();
    }

    @Override
    public boolean isHealthy() {
        return running.get() && state == CollectionState.RUNNING && !paused.get();
    }

    // ==================== LogFileReaderStrategy 接口实现 ====================

    @Override
    public void connect() throws Exception {
        doConnect();
    }

    @Override
    public void disconnect() {
        doDisconnect();
    }

    @Override
    public void initFiles() throws Exception {
        doInitFiles();
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            state = CollectionState.STARTING;
            log.info("Starting collector: name={}, type={}", getName(), getClass().getSimpleName());

            try {
                initExecutors();
                doConnect();
                doInitFiles();
                startReader();
                startConsumer();
                startCheckpointScheduler();
                state = CollectionState.RUNNING;
                log.info("Collector started successfully: name={}", getName());
            } catch (Exception e) {
                log.error("Failed to start collector: name={}, error={}", getName(), e.getMessage(), e);
                state = CollectionState.ERROR;
                stop();
            }
        } else {
            log.warn("Collector already running: name={}", getName());
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            state = CollectionState.STOPPING;
            log.info("Stopping collector: name={}", getName());

            try {
                saveCheckpointSync();
                shutdownExecutors();
                doDisconnect();
            } catch (Exception e) {
                log.error("Error while stopping collector: name={}", getName(), e);
            }

            state = CollectionState.STOPPED;
            log.info("Collector stopped: name={}, totalLines={}", getName(), collectedLines.get());
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

    // ==================== 线程管理 ====================

    protected void initExecutors() {
        readerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, getThreadNamePrefix() + "-reader");
            t.setDaemon(true);
            return t;
        });

        consumerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, getThreadNamePrefix() + "-consumer");
            t.setDaemon(true);
            return t;
        });

        checkpointScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, getThreadNamePrefix() + "-checkpoint");
            t.setDaemon(true);
            return t;
        });
    }

    protected String getThreadNamePrefix() {
        return "log-collector-" + getName();
    }

    protected void shutdownExecutors() {
        // 读取与检查点线程立即停止，避免继续读取新日志
        if (readerExecutor != null && !readerExecutor.isShutdown()) {
            readerExecutor.shutdownNow();
            awaitTermination(readerExecutor, "reader");
        }
        if (checkpointScheduler != null && !checkpointScheduler.isShutdown()) {
            checkpointScheduler.shutdownNow();
            awaitTermination(checkpointScheduler, "checkpoint");
        }

        // 消费线程优雅排空队列，减少停机丢日志
        if (consumerExecutor != null && !consumerExecutor.isShutdown()) {
            consumerExecutor.shutdown();
            try {
                if (!consumerExecutor.awaitTermination(CONSUMER_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    log.warn("consumer executor drain timeout, forcing shutdown: name={}, queueSize={}",
                            getName(), logQueue.size());
                    consumerExecutor.shutdownNow();
                    awaitTermination(consumerExecutor, "consumer(force)");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                consumerExecutor.shutdownNow();
            }
        }
    }

    private void awaitTermination(ExecutorService executor, String name) {
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("{} executor not terminated in time", name);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    // ==================== 读取与消费循环 ====================

    protected void startReader() {
        readerExecutor.submit(this::readLoop);
    }

    protected void startConsumer() {
        consumerExecutor.submit(this::consumeLoop);
        log.info("Started log consumer: name={}", getName());
    }

    /**
     * 读取循环 - 子类实现文件读取，调用 {@link #handleLine(String, long, String, long)} 处理每行
     */
    protected void readLoop() {
        log.info("Read loop started: paths={}", getFilePaths());

        long lastCheckpointTime = System.currentTimeMillis();
        long linesSinceCheckpoint = 0;

        while (running.get()) {
            try {
                while (paused.get() && running.get()) {
                    Thread.sleep(100);
                }
                if (!running.get()) break;

                doReadFiles();

                if (linesSinceCheckpoint >= config.getCheckpointInterval() ||
                        System.currentTimeMillis() - lastCheckpointTime >= config.getCheckpointIntervalMs()) {
                    saveAllCheckpoints();
                    linesSinceCheckpoint = 0;
                    lastCheckpointTime = System.currentTimeMillis();
                }

                Thread.sleep(config.getFileRotateCheckIntervalMs() > 0 ?
                        config.getFileRotateCheckIntervalMs() : 1000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in read loop", e);
                state = CollectionState.ERROR;
                break;
            }
        }

        log.info("Read loop stopped");
    }

    /**
     * 消费循环 - 从队列取出日志，批量发送到 RabbitMQ
     */
    protected void consumeLoop() {
        log.info("Consumer loop started: name={}", getName());

        List<RawLogEvent> batchBuffer = new ArrayList<>();
        long lastFlushTime = System.currentTimeMillis();
        int batchSize = DEFAULT_BATCH_SIZE;
        long flushIntervalMs = DEFAULT_FLUSH_INTERVAL_MS;

        while (running.get() || !logQueue.isEmpty()) {
            try {
                RawLogEvent event = logQueue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    batchBuffer.add(event);
                    if (batchBuffer.size() >= batchSize) {
                        sendToRabbitMQ(batchBuffer);
                        batchBuffer.clear();
                        lastFlushTime = System.currentTimeMillis();
                    }
                }
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

        if (!batchBuffer.isEmpty()) {
            sendToRabbitMQ(batchBuffer);
        }
        log.info("Consumer loop stopped: name={}", getName());
    }

    // ==================== 行处理（子类通过 handleLine 调用）====================

    /**
     * 子类每读取到一行内容，调用此方法交给基类处理。
     * 基类负责多行日志合并、检查点计数、队列入队。
     *
     * @param rawLine   原始行内容（未合并多行）
     * @param lineNumber 行号（原始行号，非合并后）
     * @param filePath  文件路径
     * @param offset    文件偏移量
     */
    protected void handleLine(String rawLine, long lineNumber, String filePath, long offset) {
        if (!running.get()) return;

        boolean isLogStart = isLogStart(rawLine);

        if (isLogStart) {
            if (!multiLineBuffer.isEmpty()) {
                flushMultiLineBuffer(filePath);
            }
            multiLineStartLineNumber = lineNumber;
            multiLineBuffer.append(rawLine);
        } else {
            if (!multiLineBuffer.isEmpty()) {
                multiLineBuffer.append("\n").append(rawLine);
            } else {
                enqueueLogEvent(rawLine, lineNumber, filePath, offset);
            }
        }
    }

    /**
     * 基类主动刷新缓冲区的多行日志。
     * 通常在文件读取结束或检查点保存时被调用。
     */
    protected void flushBuffer(String filePath) {
        if (!canFlushBufferedLogs()) return;
        if (!multiLineBuffer.isEmpty()) {
            flushMultiLineBuffer(filePath);
        }
    }

    private void flushMultiLineBuffer(String filePath) {
        if (!canFlushBufferedLogs() || multiLineBuffer.isEmpty()) return;
        String logContent = multiLineBuffer.toString();
        enqueueLogEvent(logContent, multiLineStartLineNumber, filePath, -1);
        multiLineBuffer.setLength(0);
        multiLineStartLineNumber = 0;
    }

    private boolean canFlushBufferedLogs() {
        return running.get() || state == CollectionState.STOPPING;
    }

    private void enqueueLogEvent(String line, long lineNumber, String filePath, long offset) {
        try {
            collectedLines.incrementAndGet();
            String logType = determineLogType(filePath);

            RawLogEvent event = RawLogEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .sourceId(logSource.getId())
                    .sourceName(logSource.getName())
                    .filePath(filePath)
                    .rawContent(line)
                    .lineNumber(lineNumber)
                    .fileOffset(offset >= 0 ? offset : 0)
                    .byteLength(line.getBytes(charset).length)
                    .fileInode("")
                    .collectionTime(LocalDateTime.now())
                    .logFormat(logSource.getLogFormat() != null ? logSource.getLogFormat().name() : null)
                    .logFormatPattern(logSource.getLogFormatPattern())
                    .logType(logType)
                    .traceFieldName(resolveTraceFieldName())
                    .build();

            boolean offered = logQueue.offer(event, 1, TimeUnit.SECONDS);
            if (!offered) {
                log.warn("Log queue is full, line dropped: lineNumber={}", event.getLineNumber());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (running.get()) {
                log.error("Failed to enqueue log event: content={}", line, e);
            }
        }
    }

    protected String determineLogType(String filePath) {
        String normalizedFilePath = asTrimmedString(filePath);
        if (normalizedFilePath == null) {
            return null;
        }

        // 优先使用配置中的 access/error 路径
        if (logSource.getConfig() != null) {
            String errorLogPath = asTrimmedString(logSource.getConfig().get("errorLogPath"));
            String accessLogPath = asTrimmedString(logSource.getConfig().get("accessLogPath"));
            if (normalizedFilePath.equals(errorLogPath)) return "error";
            if (normalizedFilePath.equals(accessLogPath)) return "access";
        }

        // 兼容历史数据：若未写入 config.access/error，按 paths 顺序回退（第1个为access，第2个为error）
        if (logSource.getLogFormat() == com.evelin.loganalysis.logcollection.enums.LogFormat.NGINX) {
            List<String> paths = com.evelin.loganalysis.logcollection.util.LogPathSerializer.deserializePaths(logSource.getPaths());
            if (!paths.isEmpty() && normalizedFilePath.equals(asTrimmedString(paths.get(0)))) return "access";
            if (paths.size() > 1 && normalizedFilePath.equals(asTrimmedString(paths.get(1)))) return "error";
        }

        return null;
    }

    private String asTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    protected String resolveTraceFieldName() {
        if (logSource.getConfig() == null) {
            return null;
        }
        Object value = logSource.getConfig().get("traceFieldName");
        if (value == null) {
            return null;
        }
        String fieldName = value.toString().trim();
        return fieldName.isEmpty() ? null : fieldName;
    }

    // ==================== RabbitMQ 发送 ====================

    protected void sendToRabbitMQ(List<RawLogEvent> events) {
        if (events == null || events.isEmpty()) return;
        try {
            for (RawLogEvent event : events) {
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
                        .logType(event.getLogType())
                        .traceFieldName(resolveTraceFieldName())
                        .customPattern(logSource.getCustomPattern())
                        .desensitizationConfig(buildDesensitizationConfig())
                        .build();
                String routingKey = "log.raw." + event.getSourceId();
                rabbitTemplate.convertAndSend(RabbitMQConfig.LOG_EXCHANGE, routingKey, message);
            }
            log.info("Sent {} events to RabbitMQ: name={}", events.size(), getName());
        } catch (Exception e) {
            log.error("Failed to send events to RabbitMQ: name={}", getName(), e);
        }
    }

    protected LogDesensitizationMessage.DesensitizationConfig buildDesensitizationConfig() {
        return LogDesensitizationMessage.DesensitizationConfig.builder()
                .enabled(logSource.getDesensitizationEnabled())
                .enabledRuleIds(logSource.getEnabledRuleIds())
                .customRules(buildCustomRules())
                .build();
    }

    protected List<LogDesensitizationMessage.DesensitizationConfig.CustomRule> buildCustomRules() {
        if (logSource.getCustomRules() == null) return null;
        return logSource.getCustomRules().stream()
                .map(rule -> LogDesensitizationMessage.DesensitizationConfig.CustomRule.builder()
                        .id(rule.getId())
                        .name(rule.getName())
                        .pattern(rule.getPattern())
                        .maskType(rule.getMaskType())
                        .build())
                .collect(Collectors.toList());
    }

    // ==================== 检查点管理 ====================

    protected void startCheckpointScheduler() {
        long intervalMs = config.getCheckpointIntervalMs() > 0 ? config.getCheckpointIntervalMs() : 60000;
        checkpointScheduler.scheduleAtFixedRate(
                this::saveCheckpointAsync,
                intervalMs, intervalMs, TimeUnit.MILLISECONDS
        );
    }

    protected void saveCheckpointAsync() {
        if (!running.get()) return;
        try {
            saveAllCheckpoints();
            flushBuffer(getPrimaryFilePath());
        } catch (Exception e) {
            log.warn("Failed to save checkpoint asynchronously", e);
        }
    }

    protected void saveCheckpointSync() {
        try {
            // 停止前强制保存所有文件检查点，降低非优雅停机后的重读窗口
            saveAllCheckpoints();
        } catch (Exception e) {
            log.warn("Failed to save checkpoints synchronously during shutdown", e);
        }
    }

    protected String getPrimaryFilePath() {
        List<String> paths = logSource.getPathsList();
        if (paths != null && !paths.isEmpty()) {
            return paths.get(0);
        }
        return logSource.getPath();
    }

    protected List<String> getFilePaths() {
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

    // ==================== 抽象方法 - 子类实现 ====================

    /**
     * 建立连接（如 SSH 连接）
     */
    protected abstract void doConnect() throws Exception;

    /**
     * 断开连接
     */
    protected abstract void doDisconnect();

    /**
     * 初始化文件（如打开文件句柄、加载检查点）
     */
    protected abstract void doInitFiles() throws Exception;

    /**
     * 执行文件读取逻辑。
     * 子类在此方法中遍历 fileContextMap，调用 {@link #handleLine} 处理每行。
     */
    protected abstract void doReadFiles() throws Exception;

    // ==================== LineProcessor 回调 ====================

    public void setLineProcessor(LineProcessor lineProcessor) {
        this.lineProcessor = lineProcessor;
    }

    public interface LineProcessor {
        void process(String line, long lineNumber, String filePath, long offset);
    }

    @Override
    public void shutdown() {
        doDisconnect();
    }
}
