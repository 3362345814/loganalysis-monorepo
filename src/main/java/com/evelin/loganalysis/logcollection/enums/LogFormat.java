package com.evelin.loganalysis.logcollection.enums;

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
     * Nginx 日志（通用，用于采集配置）
     * 采集时会同时采集 access 和 error 日志
     * 解析时使用 NGINX_ACCESS 和 NGINX_ERROR
     */
    NGINX,

    /**
     * Nginx Access 日志
     * 用户需要提供 log_format 字符串来生成解析正则
     * 示例: 192.168.97.4 - - [13/Mar/2026:05:13:17 +0000] "POST /api/orders HTTP/1.1" 201 95
     */
    NGINX_ACCESS,

    /**
     * Nginx Error 日志
     * 固定格式解析
     * 示例: 2026/03/13 14:26:31 [error] 1234#1234: *5678 upstream timed out
     */
    NGINX_ERROR,

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
