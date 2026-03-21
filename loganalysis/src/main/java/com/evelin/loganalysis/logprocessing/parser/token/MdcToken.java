package com.evelin.loganalysis.logprocessing.parser.token;

import com.evelin.loganalysis.logprocessing.parser.ParseResult;
import com.evelin.loganalysis.logprocessing.parser.context.ParseContext;

/**
 * MDC Token (Mapped Diagnostic Context)
 * 格式: %X{key}
 *
 * 通常以 key=value 的形式出现，如 traceId=abc123
 *
 * @author Evelin
 */
public class MdcToken extends Token {

    private final String key;

    public MdcToken(String key) {
        this.type = "MDC";
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String toRegex() {
        // MDC 值通常是键值对形式: key=value 或纯值
        return "([^\\s]*)";
    }

    @Override
    public String getFieldName() {
        return key != null ? key : "mdc";
    }

    @Override
    public void parse(ParseContext context, ParseResult result) {
        String rawValue = context.readUntil(endDelimiter);

        // 跳过 endDelimiter
        if (endDelimiter != null) {
            context.skipLiteral(endDelimiter);
        }

        // 提取 value 部分（如果有 key= 前缀）
        String value = extractValue(rawValue);

        // 填充结果
        String fieldName = getFieldName();
        result.getFields().put(fieldName, value.trim());
    }

    /**
     * 从 rawValue 中提取值
     * 例如：traceId=abc123 -> abc123
     *       abc123 -> abc123
     */
    private String extractValue(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return rawValue;
        }

        String trimmed = rawValue.trim();

        // 如果包含 =，提取 = 后面的部分
        int eqIndex = trimmed.indexOf('=');
        if (eqIndex >= 0 && eqIndex < trimmed.length() - 1) {
            return trimmed.substring(eqIndex + 1);
        }

        return trimmed;
    }
}
