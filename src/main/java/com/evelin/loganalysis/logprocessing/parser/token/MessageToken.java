package com.evelin.loganalysis.logprocessing.parser.token;

import com.evelin.loganalysis.logprocessing.parser.ParseResult;
import com.evelin.loganalysis.logprocessing.parser.context.ParseContext;

/**
 * 消息 Token
 * 格式: %msg 或 %m
 *
 * 消息是日志的最后一部分，读取直到行尾
 *
 * @author Evelin
 */
public class MessageToken extends Token {

    public MessageToken() {
        this.type = "MSG";
    }

    @Override
    public String toRegex() {
        // 消息是行尾的所有内容
        return "(.*)";
    }

    @Override
    public String getFieldName() {
        return "message";
    }

    @Override
    public void parse(ParseContext context, ParseResult result) {
        // MSG 读取直到行尾（endDelimiter 为 null）
        String rawValue = context.readUntil(endDelimiter);

        // 清理消息内容
        String message = rawValue.trim();

        // 填充结果
        result.getFields().put(getFieldName(), message);
        result.setMessage(message);

        // 尝试解析异常信息
        parseExceptionInfo(result, message);
    }

    /**
     * 尝试从消息中解析异常信息
     * 例如: NullPointerException: Cannot invoke method on null object
     */
    private void parseExceptionInfo(ParseResult result, String message) {
        if (message == null || !message.contains(":")) {
            return;
        }

        String[] parts = message.split(":", 2);
        if (parts.length < 2) {
            return;
        }

        String potentialException = parts[0].trim();
        if (potentialException.endsWith("Exception") || potentialException.endsWith("Error")) {
            result.setExceptionType(potentialException);
            result.setExceptionMessage(parts[1].trim());
        }
    }
}
