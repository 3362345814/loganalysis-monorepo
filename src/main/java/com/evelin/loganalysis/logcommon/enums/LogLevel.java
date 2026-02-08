package com.evelin.loganalysis.logcommon.enums;

/**
 * 日志级别枚举
 *
 * @author Evelin
 */
public enum LogLevel {
    /**
     * 跟踪级别，用于调试
     */
    TRACE,

    /**
     * 调试级别
     */
    DEBUG,

    /**
     * 信息级别
     */
    INFO,

    /**
     * 警告级别
     */
    WARN,

    /**
     * 错误级别
     */
    ERROR,

    /**
     * 致命级别
     */
    FATAL,

    /**
     * 未知级别
     */
    UNKNOWN;

    /**
     * 根据字符串获取日志级别
     *
     * @param level 级别字符串
     * @return 日志级别枚举
     */
    public static LogLevel fromString(String level) {
        if (level == null || level.isEmpty()) {
            return UNKNOWN;
        }
        try {
            return LogLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
