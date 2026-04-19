package com.evelin.loganalysis.logcollection.collector;

import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.config.RabbitMQConfig;
import com.evelin.loganalysis.logcollection.dto.LogDesensitizationMessage;
import com.evelin.loganalysis.logcollection.model.CollectionState;
import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logcollection.service.CheckpointManager;
import com.evelin.loganalysis.logcollection.enums.LogFormat;
import com.evelin.loganalysis.logcollection.model.LogSource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Slf4j
public abstract class AbstractLogCollector implements LogCollector {

    protected final String id;
    @Getter
    protected final LogSource logSource;
    protected final CheckpointManager checkpointManager;
    protected final CollectionConfig config;
    protected final RabbitTemplate rabbitTemplate;

    protected volatile CollectionState state = CollectionState.STOPPED;
    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected final AtomicBoolean paused = new AtomicBoolean(false);
    protected final AtomicLong collectedLines = new AtomicLong(0);

    protected final StringBuilder multiLineBuffer = new StringBuilder();
    protected long multiLineStartLineNumber = 0;
    protected Pattern logStartPattern;

    @Getter
    protected final BlockingQueue<RawLogEvent> logQueue;

    protected ExecutorService readerExecutor;
    protected ExecutorService consumerExecutor;
    protected ScheduledExecutorService checkpointScheduler;

    protected long filePointer = 0;
    protected long lastKnownFileSize = 0;

    protected static final int DEFAULT_BATCH_SIZE = 100;
    protected static final long DEFAULT_FLUSH_INTERVAL_MS = 5000;

    public AbstractLogCollector(LogSource logSource,
                                CheckpointManager checkpointManager,
                                CollectionConfig config,
                                RabbitTemplate rabbitTemplate) {
        this.logSource = logSource;
        this.checkpointManager = checkpointManager;
        this.config = config;
        this.rabbitTemplate = rabbitTemplate;

        int queueCapacity = config.getQueueCapacity() > 0 ? config.getQueueCapacity() : 10000;
        this.logQueue = new LinkedBlockingQueue<>(queueCapacity);

        this.id = logSource.getId().toString();

        initLogStartPattern();
    }

    protected void initLogStartPattern() {
        LogFormat logFormat = logSource.getLogFormat();
        if (logFormat == null) {
            logFormat = LogFormat.SPRING_BOOT;
        }

        String patternStr = getLogStartPattern(logFormat, logSource.getCustomPattern());
        if (patternStr != null) {
            this.logStartPattern = Pattern.compile(patternStr);
        }
    }

    protected String getLogStartPattern(LogFormat format, String customPattern) {
        if (customPattern != null && !customPattern.isEmpty()) {
            return customPattern;
        }
        return switch (format) {
            case SPRING_BOOT -> "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?";
            case LOG4J -> "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}";
            case NGINX, NGINX_ACCESS, NGINX_ERROR -> null;
            case JSON -> "^\\{";
            case CUSTOM -> customPattern;
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

    protected Charset getCharset() {
        String encoding = logSource.getEncoding();
        if (encoding == null || encoding.isEmpty()) {
            encoding = "UTF-8";
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            log.warn("Invalid charset: {}, using UTF-8", encoding);
            return StandardCharsets.UTF_8;
        }
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
        if (readerExecutor != null && !readerExecutor.isShutdown()) {
            readerExecutor.shutdownNow();
            try {
                if (!readerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Reader executor not terminated in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                readerExecutor.shutdownNow();
            }
        }

        if (consumerExecutor != null && !consumerExecutor.isShutdown()) {
            consumerExecutor.shutdownNow();
            try {
                if (!consumerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Consumer executor not terminated in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                consumerExecutor.shutdownNow();
            }
        }

        if (checkpointScheduler != null && !checkpointScheduler.isShutdown()) {
            checkpointScheduler.shutdownNow();
            try {
                if (!checkpointScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Checkpoint scheduler not terminated in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                checkpointScheduler.shutdownNow();
            }
        }

        if (logQueue != null) {
            logQueue.clear();
        }
    }

    protected void loadCheckpoint(String filePath) {
        try {
            var checkpoint = checkpointManager.load(
                    logSource.getId().toString(),
                    filePath
            );

            if (checkpoint != null) {
                filePointer = checkpoint.getOffset() != null ? checkpoint.getOffset() : 0L;
                lastKnownFileSize = checkpoint.getFileSize() != null ? checkpoint.getFileSize() : 0L;
                log.info("Checkpoint loaded: path={}, filePointer={}, fileSize={}",
                        filePath, filePointer, lastKnownFileSize);
            } else {
                filePointer = 0L;
                lastKnownFileSize = 0L;
                log.info("No checkpoint found, starting from beginning: path={}", filePath);
            }
        } catch (Exception e) {
            log.warn("Failed to load checkpoint: path={}", filePath, e);
            filePointer = 0L;
            lastKnownFileSize = 0L;
        }
    }

    protected void saveCheckpointSync(String filePath) {
        try {
            checkpointManager.save(
                    logSource.getId().toString(),
                    filePath,
                    filePointer,
                    lastKnownFileSize,
                    "",
                    LocalDateTime.now()
            );
            log.debug("Checkpoint saved synchronously: path={}, filePointer={}", filePath, filePointer);
        } catch (Exception e) {
            log.error("Failed to save checkpoint: path={}", filePath, e);
        }
    }

    protected void saveCheckpointSync() {
        saveCheckpointSync(getPrimaryFilePath());
    }

    protected void startCheckpointScheduler() {
        long intervalMs = config.getCheckpointIntervalMs() > 0 ?
                config.getCheckpointIntervalMs() : 60000;

        checkpointScheduler.scheduleAtFixedRate(
                this::saveCheckpointAsync,
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    protected void saveCheckpointAsync() {
        if (!running.get()) {
            return;
        }

        try {
            String filePath = getPrimaryFilePath();
            checkpointManager.save(
                    logSource.getId().toString(),
                    filePath,
                    filePointer,
                    lastKnownFileSize,
                    "",
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            log.warn("Failed to save checkpoint asynchronously", e);
        }
    }

    protected String getPrimaryFilePath() {
        List<String> paths = logSource.getPathsList();
        if (paths != null && !paths.isEmpty()) {
            return paths.get(0);
        }
        return logSource.getPath();
    }

    protected void startReader() {
        readerExecutor.submit(this::readLoop);
    }

    protected void startConsumer() {
        consumerExecutor.submit(this::consumeLoop);
        log.info("Started log consumer: name={}", getName());
    }

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

    protected void sendToRabbitMQ(List<RawLogEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

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
        if (logSource.getCustomRules() == null) {
            return null;
        }

        return logSource.getCustomRules().stream()
                .map(rule -> LogDesensitizationMessage.DesensitizationConfig.CustomRule.builder()
                        .id(rule.getId())
                        .name(rule.getName())
                        .pattern(rule.getPattern())
                        .maskType(rule.getMaskType())
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    protected void processLine(String line, long lineNumber, String filePath) {
        try {
            if (!running.get()) {
                return;
            }

            String logType = determineLogType(filePath);

            RawLogEvent event = RawLogEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .sourceId(logSource.getId())
                    .sourceName(logSource.getName())
                    .filePath(filePath)
                    .rawContent(line)
                    .lineNumber(lineNumber)
                    .fileOffset(filePointer)
                    .byteLength(line.getBytes(getCharset()).length)
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
            log.info("Collector interrupted, stopping: name={}", getName());
        } catch (Exception e) {
            if (running.get()) {
                log.error("Failed to process line: content={}", line, e);
            }
        }
    }

    protected String determineLogType(String filePath) {
        if (logSource.getConfig() != null) {
            Object errorLogPathObj = logSource.getConfig().get("errorLogPath");
            Object accessLogPathObj = logSource.getConfig().get("accessLogPath");

            String errorLogPath = errorLogPathObj != null ? errorLogPathObj.toString() : null;
            String accessLogPath = accessLogPathObj != null ? accessLogPathObj.toString() : null;

            if (filePath != null) {
                if (errorLogPath != null && filePath.equals(errorLogPath)) {
                    return "error";
                } else if (accessLogPath != null && filePath.equals(accessLogPath)) {
                    return "access";
                }
            }
        }
        return null;
    }

    protected String resolveTraceFieldName() {
        Map<String, Object> sourceConfig = logSource.getConfig();
        if (sourceConfig == null) {
            return null;
        }
        Object value = sourceConfig.get("traceFieldName");
        if (value == null) {
            return null;
        }
        String fieldName = value.toString().trim();
        return fieldName.isEmpty() ? null : fieldName;
    }

    protected void processMultiLineLog(String line, long lineNumber, String filePath) {
        if (!running.get()) {
            return;
        }

        boolean isLogStart = isLogStart(line);

        if (isLogStart) {
            if (!multiLineBuffer.isEmpty()) {
                flushMultiLineBuffer(filePath);
            }
            multiLineStartLineNumber = lineNumber;
            multiLineBuffer.append(line);
        } else {
            if (!multiLineBuffer.isEmpty()) {
                multiLineBuffer.append("\n");
            }
            multiLineBuffer.append(line);
        }
    }

    protected void flushMultiLineBuffer(String filePath) {
        if (!running.get()) {
            return;
        }
        if (!multiLineBuffer.isEmpty()) {
            String logContent = multiLineBuffer.toString();
            processLine(logContent, multiLineStartLineNumber, filePath);
            multiLineBuffer.setLength(0);
            multiLineStartLineNumber = 0;
        }
    }

    protected abstract void doConnect() throws Exception;

    protected abstract void doDisconnect();

    protected abstract void doInitFiles() throws Exception;

    protected abstract void readLoop();
}
