package com.evelin.loganalysis.logcommon.utils;

import org.springframework.stereotype.Component;

/**
 * ID生成器
 *
 * @author Evelin
 */
@Component
public class IdGenerator {

    private final SnowflakeIdGenerator snowflakeIdGenerator = new SnowflakeIdGenerator();

    /**
     * 生成UUID格式的ID（无横线）
     *
     * @return UUID字符串
     */
    public static String nextId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成雪花算法ID
     *
     * @return 雪花ID
     */
    public long nextSnowflakeId() {
        return snowflakeIdGenerator.nextId();
    }

    /**
     * 生成雪花算法ID（字符串格式）
     *
     * @return 雪花ID字符串
     */
    public String nextSnowflakeIdStr() {
        return String.valueOf(snowflakeIdGenerator.nextId());
    }

    /**
     * 生成聚合组ID
     * 格式: AGG-20260115-001
     *
     * @return 聚合组ID
     */
    public String nextGroupId() {
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String sequence = String.format("%03d", snowflakeIdGenerator.nextSequence() % 1000);
        return "AGG-" + date + "-" + sequence;
    }

    /**
     * 生成告警ID
     * 格式: ALT-20260115-001
     *
     * @return 告警ID
     */
    public String nextAlertId() {
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String sequence = String.format("%03d", snowflakeIdGenerator.nextSequence() % 1000);
        return "ALT-" + date + "-" + sequence;
    }

    /**
     * 生成分布式ID（带前缀）
     *
     * @param prefix 前缀
     * @return 带前缀的ID
     */
    public String nextIdWithPrefix(String prefix) {
        return prefix + "-" + nextId();
    }
}
