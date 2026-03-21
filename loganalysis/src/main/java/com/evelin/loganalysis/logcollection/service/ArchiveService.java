package com.evelin.loganalysis.logcollection.service;

import com.evelin.loganalysis.logcollection.config.StorageStrategyConfig;
import com.evelin.loganalysis.logcollection.model.entity.RawLogEventEntity;
import com.evelin.loganalysis.logcollection.repository.RawLogEventRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * 冷数据归档服务
 * 将超过热数据保留期的日志归档到 MinIO 对象存储
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final StorageStrategyConfig storageStrategyConfig;
    private final RawLogEventRepository rawLogEventRepository;
    private final MinioClient minioClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 定期执行归档任务
     */
    @Scheduled(fixedDelayString = "${storage.strategy.archive-schedule-hours:24}0000")
    public void archiveOldLogs() {
        if (!storageStrategyConfig.isEnabled()) {
            log.debug("分级存储未启用，跳过归档任务");
            return;
        }

        String coldStorageType = storageStrategyConfig.getColdStorageType();
        log.info("开始执行日志归档任务... (冷存储类型: {})", coldStorageType);

        try {
            int hotDataDays = storageStrategyConfig.getHotDataRetentionDays();
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(hotDataDays);

            // 查询需要归档的数据
            List<RawLogEventEntity> logsToArchive = rawLogEventRepository
                    .findByCollectionTimeBefore(cutoffTime);

            if (logsToArchive.isEmpty()) {
                log.info("没有需要归档的日志数据");
                return;
            }

            log.info("找到 {} 条需要归档的日志", logsToArchive.size());

            // 根据配置选择归档方式
            int archivedCount;
            if ("minio".equalsIgnoreCase(coldStorageType)) {
                archivedCount = archiveToMinIO(logsToArchive);
            } else {
                archivedCount = archiveToLocalFile(logsToArchive);
            }

            // 是否删除原数据
            if (storageStrategyConfig.isDeleteAfterArchive()) {
                deleteArchivedLogs(logsToArchive);
            }

            log.info("日志归档任务完成，共归档 {} 条", archivedCount);

        } catch (Exception e) {
            log.error("日志归档任务执行失败", e);
        }
    }

    /**
     * 归档数据到 MinIO
     */
    private int archiveToMinIO(List<RawLogEventEntity> logs) throws Exception {
        String bucket = storageStrategyConfig.getBucket();

        // 按日期分组归档
        int totalArchived = 0;
        LocalDate currentDate = null;
        ByteArrayOutputStream baos = null;

        try {
            for (RawLogEventEntity rawLog : logs) {
                LocalDate logDate = rawLog.getCollectionTime().toLocalDate();

                // 新的一天，创建新文件
                if (!logDate.equals(currentDate)) {
                    // 上传前一天的数据
                    if (baos != null) {
                        totalArchived += uploadToMinIO(bucket, currentDate, baos);
                    }
                    currentDate = logDate;
                    baos = new ByteArrayOutputStream();
                    log.info("开始归档 {} 的日志", currentDate.format(DATE_FORMATTER));
                }

                // 写入日志（JSON Lines 格式）
                String jsonLine = convertToJsonLine(rawLog);
                byte[] bytes = (jsonLine + "\n").getBytes();
                baos.write(bytes);
            }

            // 上传最后一天的数据
            if (baos != null && currentDate != null) {
                totalArchived += uploadToMinIO(bucket, currentDate, baos);
            }

        } finally {
            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException e) {
                    log.warn("关闭 ByteArrayOutputStream 失败", e);
                }
            }
        }

        return totalArchived;
    }

    /**
     * 上传到 MinIO（支持压缩）
     */
    private int uploadToMinIO(String bucket, LocalDate date, ByteArrayOutputStream baos) throws Exception {
        String objectName = String.format("raw_logs_%s.jsonl%s",
                date.format(DATE_FORMATTER),
                storageStrategyConfig.isCompressArchive() ? ".gz" : "");

        ByteArrayInputStream bais;
        long size;

        if (storageStrategyConfig.isCompressArchive()) {
            // 压缩数据
            ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOs = new GZIPOutputStream(compressedBaos)) {
                gzipOs.write(baos.toByteArray());
            }
            byte[] compressedData = compressedBaos.toByteArray();
            bais = new ByteArrayInputStream(compressedData);
            size = compressedData.length;
        } else {
            byte[] data = baos.toByteArray();
            bais = new ByteArrayInputStream(data);
            size = data.length;
        }

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(bais, size, -1)
                        .contentType("application/json")
                        .build()
        );

        int lineCount = countLines(baos);
        log.info("成功上传归档文件到 MinIO: {}/{} ({} 条日志)", bucket, objectName, lineCount);
        return lineCount;
    }

    /**
     * 归档数据到本地文件（备用方案）
     */
    private int archiveToLocalFile(List<RawLogEventEntity> logs) throws IOException {
        String archivePath = storageStrategyConfig.getArchivePath();
        Path baseDir = Paths.get(archivePath);

        // 创建归档目录
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }

        // 按日期分组归档
        int totalArchived = 0;
        LocalDate currentDate = null;
        BufferedWriter writer = null;

        try {
            for (RawLogEventEntity rawLog : logs) {
                LocalDate logDate = rawLog.getCollectionTime().toLocalDate();

                // 新的一天，创建新文件
                if (!logDate.equals(currentDate)) {
                    if (writer != null) {
                        writer.close();
                    }
                    currentDate = logDate;
                    String fileName = "raw_logs_" + currentDate.format(DATE_FORMATTER) + ".jsonl";
                    if (storageStrategyConfig.isCompressArchive()) {
                        fileName += ".gz";
                    }
                    Path filePath = baseDir.resolve(fileName);
                    writer = createWriter(filePath);
                    log.info("创建归档文件: {}", filePath);
                }

                // 写入日志（JSON Lines 格式）
                String jsonLine = convertToJsonLine(rawLog);
                writer.write(jsonLine);
                writer.newLine();
                totalArchived++;
            }

        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        return totalArchived;
    }

    /**
     * 创建写入器（支持压缩）
     */
    private BufferedWriter createWriter(Path filePath) throws IOException {
        if (storageStrategyConfig.isCompressArchive()) {
            OutputStream os = new FileOutputStream(filePath.toFile());
            OutputStream gzipOs = new GZIPOutputStream(os);
            return new BufferedWriter(new OutputStreamWriter(gzipOs));
        } else {
            return new BufferedWriter(new FileWriter(filePath.toFile(), true));
        }
    }

    /**
     * 转换为 JSON Lines 格式
     */
    private String convertToJsonLine(RawLogEventEntity log) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(log.getId()).append("\",");
        sb.append("\"eventId\":\"").append(escapeJson(log.getEventId())).append("\",");
        sb.append("\"sourceId\":\"").append(log.getSourceId()).append("\",");
        sb.append("\"sourceName\":\"").append(escapeJson(log.getSourceName())).append("\",");
        sb.append("\"filePath\":\"").append(escapeJson(log.getFilePath())).append("\",");
        sb.append("\"rawContent\":\"").append(escapeJson(log.getRawContent())).append("\",");
        sb.append("\"lineNumber\":").append(log.getLineNumber()).append(",");
        sb.append("\"collectionTime\":\"").append(log.getCollectionTime()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    /**
     * JSON 字符串转义
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 统计行数（用于日志计数）
     */
    private int countLines(ByteArrayOutputStream baos) {
        String content = baos.toString();
        if (content.isEmpty()) return 0;
        return content.split("\n").length;
    }

    /**
     * 删除已归档的日志
     */
    @Transactional
    public void deleteArchivedLogs(List<RawLogEventEntity> logs) {
        List<UUID> ids = logs.stream()
                .map(RawLogEventEntity::getId)
                .toList();
        rawLogEventRepository.deleteAllByIdInBatch(ids);
        log.info("已删除 {} 条已归档的日志", ids.size());
    }

    /**
     * 清理超过温数据保留期的日志（彻底删除）
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    @Transactional
    public void cleanupExpiredLogs() {
        if (!storageStrategyConfig.isEnabled()) {
            return;
        }

        int warmDataDays = storageStrategyConfig.getWarmDataRetentionDays();
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(warmDataDays);

        long deletedCount = rawLogEventRepository.deleteByCollectionTimeBefore(cutoffTime);
        log.info("清理了 {} 条超过保留期的日志（保留期{}天）", deletedCount, warmDataDays);
    }
}
