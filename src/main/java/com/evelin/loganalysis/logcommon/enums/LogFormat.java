package com.evelin.loganalysis.logcommon.enums;

/**
 * 日志格式枚举
 *
 * @author Evelin
 */
public enum LogFormat {
    /**
     * Spring Boot 日志格式
     * 示例: 2026-03-10 11:37:02.316 [scheduling-1] ERROR c.e.a.service.LogGeneratorService - message
     */
    SPRING_BOOT,

    /**
     * Log4j 日志格式
     * 示例: 2026-03-10 11:37:02 ERROR [thread] level class - message
     */
    LOG4J,

    /**
     * Nginx 日志格式
     * 示例: 127.0.0.1 - - [10/Mar/2026:11:37:02 +0000] "GET /path HTTP/1.1" 200 123
     */
    NGINX,

    /**
     * JSON 格式日志
     * 每行一个完整的 JSON 对象
     */
    JSON,

    /**
     * 普通文本格式
     * 不进行特殊解析
     */
    PLAIN_TEXT,

    /**
     * 自定义正则格式
     * 需要配置自定义正则表达式
     */
    CUSTOM
}
