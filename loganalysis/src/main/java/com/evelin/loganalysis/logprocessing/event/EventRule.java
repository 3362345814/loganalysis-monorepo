package com.evelin.loganalysis.logprocessing.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 事件规则实体
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRule {

    /**
     * 规则ID
     */
    private String id;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 规则类型: KEYWORD/REGEX/LEVEL/THRESHOLD
     */
    private String ruleType;

    /**
     * 匹配模式
     */
    private String pattern;

    /**
     * 正则表达式标志
     */
    private String patternFlags;

    /**
     * 匹配模式: EQUALS/CONTAINS/STARTS_WITH/ENDS_WITH/REGEX
     */
    private String matchMode;

    /**
     * 是否大小写敏感
     */
    private boolean caseSensitive;

    /**
     * 事件级别
     */
    private String eventLevel;

    /**
     * 事件分类
     */
    private String eventCategory;

    /**
     * 优先级 (数字越大优先级越高)
     */
    private int priority;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
