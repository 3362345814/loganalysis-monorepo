package com.evelin.loganalysis.logcollection.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 脱敏消息 DTO
 * 用于 RabbitMQ 消息传递
 *
 * @author Evelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogDesensitizationMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    private UUID messageId;

    /**
     * 日志源ID
     */
    private UUID sourceId;

    /**
     * 日志源名称
     */
    private String sourceName;

    /**
     * 日志文件路径
     */
    private String filePath;

    /**
     * 原始日志内容
     */
    private String rawContent;

    /**
     * 行号
     */
    private Long lineNumber;

    /**
     * 文件偏移量
     */
    private Long offset;

    /**
     * 采集时间
     */
    private LocalDateTime collectionTime;

    /**
     * 脱敏配置
     */
    private DesensitizationConfig desensitizationConfig;

    /**
     * 脱敏配置类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DesensitizationConfig implements Serializable {
        
        private static final long serialVersionUID = 1L;
        
        /**
         * 是否启用脱敏
         */
        private Boolean enabled;

        /**
         * 启用的规则ID列表
         */
        private List<String> enabledRuleIds;

        /**
         * 自定义规则列表
         */
        private List<CustomRule> customRules;

        /**
         * 自定义脱敏规则
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CustomRule implements Serializable {
            private static final long serialVersionUID = 1L;
            
            /**
             * 规则ID
             */
            private String id;

            /**
             * 规则名称
             */
            private String name;

            /**
             * 正则表达式
             */
            private String pattern;

            /**
             * 脱敏类型：FULL/PARTIAL/HASH
             */
            private String maskType;

            /**
             * 替换内容
             */
            private String replacement;
        }
    }
}
