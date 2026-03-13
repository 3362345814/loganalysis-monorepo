package com.evelin.loganalysis.logcollection.collector;

import com.evelin.loganalysis.logcollection.config.CollectionConfig;
import com.evelin.loganalysis.logcollection.config.RabbitMQConfig;
import com.evelin.loganalysis.logcollection.dto.LogDesensitizationMessage;
import com.evelin.loganalysis.logcollection.model.CollectionCheckpoint;
import com.evelin.loganalysis.logcollection.model.CollectionState;
import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logcollection.service.CheckpointManager;
import com.evelin.loganalysis.logcollection.service.RawLogEventService;
import com.evelin.loganalysis.logcommon.model.LogSource;
import com.jcraft.jsch.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSH远程文件日志采集器
 * <p>
 * 通过SSH协议连接远程服务器，采集远程日志文件，支持：
 * - 断点续读（从上次停止位置继续读取）
 * - 文件轮转检测
 * - 实时监听（定时检查文件变化）
 * - 背压处理（有界队列缓冲采集任务）
 * <p>
 * 注意：此类不是Spring Bean，由外部（如 CollectorFactory）负责创建和管理
 *
 * @author Evelin
 */
@Slf4j
public class SshRemoteCollector implements LogCollector {

    private final String id;
    @Getter
    private final LogSource logSource;
    private final CheckpointManager checkpointManager;
    private final CollectionConfig config;

    private volatile CollectionState state = CollectionState.STOPPED;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicLong collectedLines = new AtomicLong(0);

    private final StringBuilder multiLineBuffer = new StringBuilder();
    private long multiLineStartLineNumber = 0;

    private final BlockingQueue<RawLogEvent> logQueue;
    private final ExecutorService readerExecutor;
    private final ExecutorService consumerExecutor;
    private final ScheduledExecutorService checkpointScheduler;
    private final RabbitTemplate rabbitTemplate;

    private Session session;
    private ChannelSftp channelSftp;

    private long filePointer = 0;
    private long lastKnownFileSize = 0;

    public SshRemoteCollector(LogSource logSource,
                              CheckpointManager checkpointManager,
                              CollectionConfig config,
                              RawLogEventService rawLogEventService,
                              RabbitTemplate rabbitTemplate) {
        this.logSource = logSource;
        this.checkpointManager = checkpointManager;
        this.config = config;
        this.rabbitTemplate = rabbitTemplate;

        int queueCapacity = config.getQueueCapacity() > 0 ? config.getQueueCapacity() : 10000;
        this.logQueue = new LinkedBlockingQueue<>(queueCapacity);

        this.readerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ssh-reader-" + logSource.getName());
            t.setDaemon(true);
            return t;
        });

        this.consumerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ssh-consumer-" + logSource.getName());
            t.setDaemon(true);
            return t;
        });

        this.checkpointScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ssh-checkpoint-" + logSource.getName());
            t.setDaemon(true);
            return t;
        });

        this.id = logSource.getId().toString();
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
            state = CollectionState.RUNNING;
            log.info("Starting SSH collector: name={}, host={}, path={}",
                    getName(), logSource.getHost(), logSource.getPath());

            try {
                connect();
                loadCheckpoint();
                startReader();
                startConsumer();
                startCheckpointScheduler();
                log.info("SSH collector started: name={}", getName());
            } catch (Exception e) {
                log.error("Failed to start SSH collector: name={}", getName(), e);
                running.set(false);
                state = CollectionState.ERROR;
                throw new RuntimeException("Failed to start SSH collector", e);
            }
        } else {
            log.warn("Collector already running: name={}", getName());
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            state = CollectionState.STOPPING;
            log.info("Stopping SSH collector: name={}", getName());

            try {
                saveCheckpointSync();
                shutdownExecutors();
                disconnect();
            } catch (Exception e) {
                log.error("Error while stopping SSH collector: name={}", getName(), e);
            }

            state = CollectionState.STOPPED;
            log.info("SSH collector stopped: name={}, totalLines={}", getName(), collectedLines.get());
        } else {
            log.warn("Collector is not running: name={}", getName());
        }
    }

    @Override
    public void pause() {
        if (running.get() && paused.compareAndSet(false, true)) {
            state = CollectionState.PAUSED;
            log.info("SSH collector paused: name={}", getName());
        }
    }

    @Override
    public void resume() {
        if (running.get() && paused.compareAndSet(true, false)) {
            state = CollectionState.RUNNING;
            log.info("SSH collector resumed: name={}", getName());
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public long getCollectedLines() {
        return collectedLines.get();
    }

    @Override
    public boolean isHealthy() {
        return running.get() && state == CollectionState.RUNNING && !paused.get();
    }

    public BlockingQueue<RawLogEvent> getLogQueue() {
        return logQueue;
    }

    private void connect() throws JSchException, SftpException {
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

    private void disconnect() {
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
    }

    private void loadCheckpoint() {
        try {
            CollectionCheckpoint checkpoint = checkpointManager.load(
                    logSource.getId().toString(),
                    logSource.getPath()
            );

            if (checkpoint != null) {
                filePointer = checkpoint.getOffset() != null ? checkpoint.getOffset() : 0;
                lastKnownFileSize = checkpoint.getFileSize() != null ? checkpoint.getFileSize() : 0;
                log.info("Checkpoint loaded: name={}, filePointer={}", getName(), filePointer);
            } else {
                filePointer = 0;
                lastKnownFileSize = 0;
                log.info("No checkpoint found, starting from beginning: name={}", getName());
            }
        } catch (Exception e) {
            log.warn("Failed to load checkpoint, starting from beginning: name={}", getName(), e);
            filePointer = 0;
            lastKnownFileSize = 0;
        }
    }

    private void saveCheckpointSync() {
        try {
            checkpointManager.save(
                    logSource.getId().toString(),
                    logSource.getPath(),
                    filePointer,
                    lastKnownFileSize,
                    "",
                    java.time.LocalDateTime.now()
            );
            log.info("Checkpoint saved synchronously: name={}, filePointer={}", getName(), filePointer);
        } catch (Exception e) {
            log.error("Failed to save checkpoint: name={}", getName(), e);
        }
    }

    private void startReader() {
        readerExecutor.submit(this::readLoop);
    }

    private void readLoop() {
        log.info("SSH read loop started: path={}", logSource.getPath());

        Charset charset = getCharset();
        String tempFilePath = "/tmp/ssh_log_" + logSource.getId() + ".tmp";

        while (running.get()) {
            try {
                while (paused.get() && running.get()) {
                    Thread.sleep(100);
                }

                if (!running.get()) {
                    break;
                }

                try {
                    channelSftp.get(logSource.getPath(), tempFilePath);
                } catch (SftpException e) {
                    log.warn("Failed to download file via SFTP, retrying: path={}, error={}",
                            logSource.getPath(), e.getMessage());
                    Thread.sleep(2000);
                    continue;
                }

                File tempFile = new File(tempFilePath);
                if (!tempFile.exists() || tempFile.length() == 0) {
                    log.warn("Downloaded file is empty or not found, retrying: path={}", logSource.getPath());
                    Thread.sleep(2000);
                    continue;
                }

                long currentFileSize = tempFile.length();

                if (currentFileSize < lastKnownFileSize) {
                    log.info("File rotation detected: oldSize={}, newSize={}", lastKnownFileSize, currentFileSize);
                    filePointer = 0;
                }

                lastKnownFileSize = currentFileSize;

                if (filePointer >= currentFileSize) {
                    Thread.sleep(1000);
                    continue;
                }

                processFileContent(tempFile, charset);

                tempFile.delete();

                Thread.sleep(config.getFileRotateCheckIntervalMs() > 0 ?
                        config.getFileRotateCheckIntervalMs() : 1000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Error in SSH read loop: name={}", getName(), e);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        new File(tempFilePath).delete();
        log.info("SSH read loop stopped: name={}", getName());
    }

    private void processFileContent(File file, Charset charset) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(filePointer);

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

                filePointer += bytesRead;

                String content = new String(readBuffer, charset);
                lineBuilder.append(content);

                int lineIndex;
                while ((lineIndex = lineBuilder.indexOf("\n")) >= 0) {
                    String line = lineBuilder.substring(0, lineIndex);
                    lineBuilder.delete(0, lineIndex + 1);

                    if (!line.isEmpty()) {
                        long lineNum = collectedLines.incrementAndGet();
                        processMultiLineLog(line, lineNum);
                    }
                }
            }

            if (lineBuilder.length() > 0) {
                String remainingLine = lineBuilder.toString();
                if (!remainingLine.isEmpty()) {
                    long lineNum = collectedLines.incrementAndGet();
                    processMultiLineLog(remainingLine, lineNum);
                }
            }
        }
    }

    private void processMultiLineLog(String line, long lineNumber) {
        if (!running.get()) {
            return;
        }

        boolean isLogStart = isLogStart(line);

        if (isLogStart) {
            if (!multiLineBuffer.isEmpty()) {
                flushMultiLineBuffer();
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

    private void processLine(String line, long lineNumber) {
        try {
            if (!running.get()) {
                return;
            }

            Charset charset = getCharset();
            RawLogEvent event = RawLogEvent.create(
                    logSource.getId(),
                    logSource.getName(),
                    logSource.getPath(),
                    line,
                    lineNumber,
                    filePointer,
                    line.getBytes(charset).length,
                    "",
                    java.time.LocalDateTime.now(),
                    logSource.getLogFormat() != null ? logSource.getLogFormat().name() : null
            );

            boolean offered = logQueue.offer(event, 1, TimeUnit.SECONDS);

            if (!offered)
                log.warn("Log queue is full, line dropped: lineNumber={}, queueSize={}",
                        event.getLineNumber(), logQueue.size());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Collector interrupted, stopping: name={}", getName());
        } catch (Exception e) {
            if (running.get()) {
                log.error("Failed to process line: content={}", line, e);
            }
        }
    }

    private boolean isLogStart(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }

        String logFormat = logSource.getLogFormat() != null ? logSource.getLogFormat().name() : null;
        if (logFormat == null) {
            logFormat = "SPRING_BOOT";
        }

        switch (logFormat) {
            case "SPRING_BOOT":
            case "LOG4J":
                return line.matches("^\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}.*");
            case "NGINX":
                return line.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*") 
                    || line.matches("^\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}.*");
            case "JSON":
                return line.trim().startsWith("{");
            default:
                return line.matches("^\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}.*");
        }
    }

    private Charset getCharset() {
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

    private void startCheckpointScheduler() {
        checkpointScheduler.scheduleAtFixedRate(
                this::saveCheckpointAsync,
                config.getCheckpointIntervalMs(),
                config.getCheckpointIntervalMs(),
                TimeUnit.MILLISECONDS
        );
    }

    private void saveCheckpointAsync() {
        if (!running.get()) {
            return;
        }

        try {
            checkpointManager.save(
                    logSource.getId().toString(),
                    logSource.getPath(),
                    filePointer,
                    lastKnownFileSize,
                    "",
                    java.time.LocalDateTime.now()
            );
        } catch (Exception e) {
            log.warn("Failed to save checkpoint asynchronously", e);
        }
    }

    private void startConsumer() {
        consumerExecutor.submit(this::consumeLoop);
        log.info("Started log consumer: name={}", getName());
    }

    private void consumeLoop() {
        log.info("Consumer loop started: name={}", getName());

        List<RawLogEvent> batchBuffer = new ArrayList<>();
        long lastFlushTime = System.currentTimeMillis();
        int batchSize = 100;
        long flushIntervalMs = 5000;

        log.info("Consumer config: batchSize={}, flushIntervalMs={}, queueCapacity={}",
                batchSize, flushIntervalMs, logQueue.size());

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

    private void sendToRabbitMQ(List<RawLogEvent> events) {
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
                        .customPattern(logSource.getCustomPattern())
                        .desensitizationConfig(buildDesensitizationConfig())
                        .build();

                String routingKey = "log.raw." + event.getSourceId();
                rabbitTemplate.convertAndSend(RabbitMQConfig.LOG_EXCHANGE, routingKey, message);
            }

            log.debug("Sent {} events to RabbitMQ: name={}", events.size(), getName());
        } catch (Exception e) {
            log.error("Failed to send events to RabbitMQ: name={}", getName(), e);
        }
    }

    private LogDesensitizationMessage.DesensitizationConfig buildDesensitizationConfig() {
        return LogDesensitizationMessage.DesensitizationConfig.builder()
                .enabled(logSource.getDesensitizationEnabled())
                .enabledRuleIds(logSource.getEnabledRuleIds())
                .customRules(buildCustomRules())
                .build();
    }

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
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    private void shutdownExecutors() {
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
}
