package com.evelin.loganalysis.logalert.enums;

/**
 * 告警规则类型枚举
 *
 * @author Evelin
 */
public enum RuleType {
    /**
     * 关键词匹配
     */
    KEYWORD,

    /**
     * 正则表达式匹配
     */
    REGEX,

    /**
     * 日志级别匹配
     */
    LEVEL,

    /**
     * 阈值匹配（数量阈值，如5分钟内>100条错误）
     */
    THRESHOLD,

    /**
     * 组合条件
     */
    COMBINATION
}
