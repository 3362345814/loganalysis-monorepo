package com.evelin.loganalysis.logprocessing.parser.token;

import com.evelin.loganalysis.logprocessing.parser.ParseResult;
import com.evelin.loganalysis.logprocessing.parser.context.ParseContext;

/**
 * 日志级别 Token
 * 格式: %level 或 %-5level
 *
 * @author Evelin
 */
public class LevelToken extends Token {

    private final int width;
    private final boolean leftAlign;

    public LevelToken(int width, boolean leftAlign) {
        this.type = "LEVEL";
        this.width = width;
        this.leftAlign = leftAlign;
    }

    public LevelToken(int width) {
        this(width, false);
    }

    public int getWidth() {
        return width;
    }

    public boolean isLeftAlign() {
        return leftAlign;
    }

    @Override
    public String toRegex() {
        if (width > 0) {
            if (leftAlign) {
                return "([A-Z]+\\s{0," + (width - 1) + "})";
            } else {
                return "(\\s{0," + (width - 1) + "}[A-Z]+)";
            }
        }
        return "(ERROR|WARN|INFO|DEBUG|TRACE|FATAL)";
    }

    @Override
    public String getFieldName() {
        return "level";
    }

    @Override
    public void parse(ParseContext context, ParseResult result) {
        String rawValue = context.readUntil(endDelimiter);

        // 跳过 endDelimiter
        if (endDelimiter != null) {
            context.skipLiteral(endDelimiter);
        }

        // 标准化日志级别
        String level = normalizeLevel(rawValue);

        // 填充结果
        result.getFields().put(getFieldName(), level);
        result.setLevel(level);
    }

    /**
     * 标准化日志级别
     * 处理带空格的级别（如 "ERROR " -> "ERROR"）
     */
    private String normalizeLevel(String value) {
        if (value == null) {
            return null;
        }
        String level = value.trim().toUpperCase();
        return level.replaceAll("\\s+", "");
    }
}
