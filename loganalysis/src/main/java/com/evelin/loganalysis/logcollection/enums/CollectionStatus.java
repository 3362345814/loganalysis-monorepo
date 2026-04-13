package com.evelin.loganalysis.logcollection.enums;

/**
 * 采集状态枚举
 *
 * @author Evelin
 */
public enum CollectionStatus {
    /**
     * 停止状态
     */
    STOPPED,

    /**
     * 运行中
     */
    RUNNING,

    /**
     * 暂停中
     */
    PAUSED,

    /**
     * 停止中（排空队列）
     */
    STOPPING,

    /**
     * 错误状态
     */
    ERROR,

    /**
     * 初始化中
     */
    INITIALIZING
}
