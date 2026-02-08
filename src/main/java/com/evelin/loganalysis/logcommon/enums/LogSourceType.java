package com.evelin.loganalysis.logcommon.enums;

/**
 * 日志源类型枚举
 *
 * @author Evelin
 */
public enum LogSourceType {
    /**
     * 本地文件
     */
    LOCAL_FILE,

    /**
     * SSH远程文件
     */
    SSH,

    /**
     * HTTP API
     */
    HTTP,

    /**
     * Kafka消息队列
     */
    KAFKA,

    /**
     * RabbitMQ消息队列
     */
    RABBITMQ,

    /**
     * 数据库
     */
    DATABASE
}
