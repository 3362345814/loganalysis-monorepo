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
     * 错误状态
     */
    ERROR,

    /**
     * 初始化中
     */
    INITIALIZING
}
