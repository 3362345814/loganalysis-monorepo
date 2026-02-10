package com.evelin.loganalysis.logcollection.model;

/**
 * 采集器状态枚举
 *
 * @author Evelin
 */
public enum CollectionState {
    /**
     * 停止状态
     */
    STOPPED,

    /**
     * 启动中
     */
    STARTING,

    /**
     * 运行中
     */
    RUNNING,

    /**
     * 暂停中
     */
    PAUSED,

    /**
     * 停止中
     */
    STOPPING,

    /**
     * 错误状态
     */
    ERROR,

    /**
     * 未知状态
     */
    UNKNOWN
}
