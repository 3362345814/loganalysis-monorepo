package com.evelin.loganalysis.logprocessing.parser.token;

import com.evelin.loganalysis.logprocessing.parser.ParseResult;
import com.evelin.loganalysis.logprocessing.parser.context.ParseContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 日期 Token
 * 格式: %d{yyyy-MM-dd HH:mm:ss} 或 %d{format}
 *
 * @author Evelin
 */
public class DateToken extends Token {

    private final String format;
    private DateTimeFormatter formatter;

    public DateToken(String format) {
        this.type = "DATE";
        // 默认格式
        this.format = (format == null || format.isEmpty()) ? "yyyy-MM-dd HH:mm:ss" : format;
        this.formatter = DateTimeFormatter.ofPattern(this.format);
    }

    public String getFormat() {
        return format;
    }

    public DateTimeFormatter getDateTimeFormatter() {
        return formatter;
    }

    @Override
    public String toRegex() {
        // 日期格式的正则匹配
        String regex = format
                .replace("yyyy", "\\d{4}")
                .replace("MM", "\\d{2}")
                .replace("dd", "\\d{2}")
                .replace("HH", "\\d{2}")
                .replace("mm", "\\d{2}")
                .replace("ss", "\\d{2}")
                .replace("SSS", "\\d{3}")
                .replace("SSSSSS", "\\d{6}");

        return regex;
    }

    /**
     * 获取用于匹配的正则表达式（不带捕获组）
     */
    public String toMatchRegex() {
        return toRegex();
    }

    @Override
    public String getFieldName() {
        return "timestamp";
    }

    @Override
    public void parse(ParseContext context, ParseResult result) {
        // DateToken 使用 format 来解析，而不是 endDelimiter
        String rawValue = context.readUntilPattern(toMatchRegex());

        // 跳过 endDelimiter（如果存在）
        if (endDelimiter != null) {
            context.skipLiteral(endDelimiter);
        }

        // 解析时间戳
        LocalDateTime timestamp = parseTimestamp(rawValue);

        // 填充结果
        fillResult(result, rawValue);

        // 设置 ParseResult 的 timestamp 字段
        result.setTimestamp(timestamp);
    }

    /**
     * 解析时间戳字符串
     */
    private LocalDateTime parseTimestamp(String value) {
        if (value == null || value.isEmpty()) {
            return LocalDateTime.now();
        }

        String trimmed = value.trim();

        // 先尝试使用配置的格式
        try {
            return LocalDateTime.parse(trimmed, formatter);
        } catch (DateTimeParseException e) {
            // 忽略，尝试其他格式
        }

        // 尝试常见格式（处理 , 和 . 的毫秒分隔符差异）
        String normalized = trimmed.replace(',', '.');

        String[] formats = {
                "yyyy-MM-dd HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss.SSSSS",
                "yyyy-MM-dd HH:mm:ss"
        };

        for (String fmt : formats) {
            try {
                DateTimeFormatter fmtFormatter = DateTimeFormatter.ofPattern(fmt);
                return LocalDateTime.parse(trimmed, fmtFormatter);
            } catch (DateTimeParseException e) {
                // 尝试下一个格式
            }
        }

        return LocalDateTime.now();
    }
}
