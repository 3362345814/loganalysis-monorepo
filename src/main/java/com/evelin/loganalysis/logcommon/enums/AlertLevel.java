package com.evelin.loganalysis.logcommon.enums;

/**
 * 告警级别枚举
 *
 * @author Evelin
 */
public enum AlertLevel {
    /**
     * 紧急级别 - P1
     */
    CRITICAL,

    /**
     * 高风险级别 - P2
     */
    HIGH,

    /**
     * 中等级别 - P3
     */
    MEDIUM,

    /**
     * 低风险级别 - P4
     */
    LOW,

    /**
     * 信息级别
     */
    INFO;

    /**
     * 根据字符串获取告警级别
     *
     * @param level 级别字符串
     * @return 告警级别枚举
     */
    public static AlertLevel fromString(String level) {
        if (level == null || level.isEmpty()) {
            return INFO;
        }
        try {
            return AlertLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO;
        }
    }

    /**
     * 获取优先级数值（数值越大优先级越高）
     *
     * @return 优先级数值
     */
    public int getPriority() {
        return switch (this) {
            case CRITICAL -> 4;
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
            case INFO -> 0;
        };
    }
}
