package com.evelin.loganalysis.logalert.enums;

/**
 * 告警状态枚举
 *
 * @author Evelin
 */
public enum AlertStatus {
    /**
     * 待处理
     */
    PENDING,

    /**
     * 已确认
     */
    ACKNOWLEDGED,

    /**
     * 处理中
     */
    PROCESSING,

    /**
     * 已解决
     */
    RESOLVED,

    /**
     * 已忽略
     */
    IGNORED,

    /**
     * 已过期
     */
    EXPIRED
}
