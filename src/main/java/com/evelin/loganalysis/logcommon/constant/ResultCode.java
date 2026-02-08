package com.evelin.loganalysis.logcommon.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 *
 * @author Evelin
 */
@Getter
@AllArgsConstructor
public enum ResultCode implements ErrorCode {

    // ========== 成功 ==========
    SUCCESS(0, "成功"),

    // ========== 通用错误 400-499 ==========
    PARAM_ERROR(400, "参数错误"),
    NOT_FOUND(404, "资源不存在"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    INTERNAL_ERROR(500, "内部服务器错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),

    // ========== 业务错误 1000-1999 ==========
    BUSINESS_ERROR(1001, "业务异常"),
    DATA_NOT_FOUND(1002, "数据不存在"),
    DATA_ALREADY_EXISTS(1003, "数据已存在"),
    OPERATION_FAILED(1004, "操作失败"),

    // ========== 采集错误 2000-2999 ==========
    COLLECTION_ERROR(2001, "日志采集异常"),
    SOURCE_NOT_FOUND(2002, "日志源不存在"),
    SOURCE_DISABLED(2003, "日志源已禁用"),
    SOURCE_CONNECT_FAILED(2004, "日志源连接失败"),
    FILE_NOT_FOUND(2005, "文件不存在"),
    FILE_READ_ERROR(2006, "文件读取错误"),
    CHECKPOINT_ERROR(2007, "检查点保存失败"),

    // ========== AI分析错误 3000-3999 ==========
    LLM_ERROR(3001, "AI分析服务异常"),
    ANALYSIS_TIMEOUT(3002, "分析超时"),
    PROMPT_BUILD_ERROR(3003, "Prompt构建失败"),
    RESPONSE_PARSE_ERROR(3004, "响应解析失败"),

    // ========== 告警错误 4000-4999 ==========
    ALERT_ERROR(4001, "告警服务异常"),
    ALERT_RULE_NOT_FOUND(4002, "告警规则不存在"),
    NOTIFICATION_FAILED(4003, "通知发送失败"),

    // ========== 规则配置错误 5000-5999 ==========
    RULE_NOT_FOUND(5001, "规则不存在"),
    RULE_PARSE_ERROR(5002, "规则解析失败"),
    RULE_VALIDATION_ERROR(5003, "规则验证失败");

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误信息
     */
    private final String message;

    /**
     * 根据错误码获取枚举
     *
     * @param code 错误码
     * @return 错误码枚举
     */
    public static ResultCode fromCode(int code) {
        for (ResultCode resultCode : values()) {
            if (resultCode.getCode() == code) {
                return resultCode;
            }
        }
        return INTERNAL_ERROR;
    }
}
