package com.evelin.loganalysis.logprocessing.parser.token;

import com.evelin.loganalysis.logprocessing.parser.ParseResult;
import com.evelin.loganalysis.logprocessing.parser.context.ParseContext;

/**
 * 异常堆栈 Token
 * 格式: %throwable / %exception / %ex
 *
 * 该字段是可选的：
 * - 日志没有异常时，解析为空
 * - 有异常时，提取完整堆栈并尝试解析异常类型和异常信息
 */
public class ThrowableToken extends Token {

    public ThrowableToken() {
        this.type = "THROWABLE";
    }

    @Override
    public String toRegex() {
        return "(?s)(.*)?";
    }

    @Override
    public String getFieldName() {
        return "throwable";
    }

    @Override
    public void parse(ParseContext context, ParseResult result) {
        String rawValue = context.readUntil(endDelimiter);
        if (endDelimiter != null) {
            context.skipLiteral(endDelimiter);
        }

        String throwable = rawValue == null ? "" : rawValue.strip();
        if (throwable.isEmpty()) {
            result.getFields().put("hasThrowable", false);
            return;
        }

        result.getFields().put(getFieldName(), throwable);
        result.getFields().put("hasThrowable", true);
        result.setStackTrace(throwable);

        if (result.getExceptionType() == null || result.getExceptionMessage() == null) {
            parseExceptionInfo(result, throwable);
        }
    }

    private void parseExceptionInfo(ParseResult result, String throwable) {
        String firstLine = null;
        for (String line : throwable.split("\\r?\\n")) {
            String trimmed = line == null ? "" : line.trim();
            if (!trimmed.isEmpty()) {
                firstLine = trimmed;
                break;
            }
        }

        if (firstLine == null || firstLine.isEmpty()) {
            return;
        }

        int colonIndex = firstLine.indexOf(':');
        if (colonIndex > 0) {
            String exceptionType = firstLine.substring(0, colonIndex).trim();
            String exceptionMessage = firstLine.substring(colonIndex + 1).trim();
            if (result.getExceptionType() == null && !exceptionType.isEmpty()) {
                result.setExceptionType(exceptionType);
            }
            if (result.getExceptionMessage() == null && !exceptionMessage.isEmpty()) {
                result.setExceptionMessage(exceptionMessage);
            }
            return;
        }

        if ((firstLine.endsWith("Exception") || firstLine.endsWith("Error")) && result.getExceptionType() == null) {
            result.setExceptionType(firstLine);
        }
    }
}
