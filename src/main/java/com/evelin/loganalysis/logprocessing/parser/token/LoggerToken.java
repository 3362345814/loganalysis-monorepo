package com.evelin.loganalysis.logprocessing.parser.token;

import com.evelin.loganalysis.logprocessing.parser.ParseResult;
import com.evelin.loganalysis.logprocessing.parser.context.ParseContext;

/**
 * Logger Token
 * 格式: %logger 或 %logger{length}
 *
 * @author Evelin
 */
public class LoggerToken extends Token {

    private final int maxLength;

    public LoggerToken(int maxLength) {
        this.type = "LOGGER";
        this.maxLength = maxLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    @Override
    public String toRegex() {
        // Logger 名称通常是包名+类名
        return "([\\w\\.]+)";
    }

    @Override
    public String getFieldName() {
        return "logger";
    }

    @Override
    public void parse(ParseContext context, ParseResult result) {
        String rawValue = context.readUntil(endDelimiter);

        // 跳过 endDelimiter
        if (endDelimiter != null) {
            context.skipLiteral(endDelimiter);
        }

        String logger = rawValue.trim();

        // 填充结果
        result.getFields().put(getFieldName(), logger);
        result.setLogger(logger);

        // 解析 className, methodName, fileName
        parseClassInfo(result, logger);
    }

    /**
     * 解析类信息
     * 例如: com.demo.UserService
     */
    private void parseClassInfo(ParseResult result, String logger) {
        if (logger == null || logger.isEmpty()) {
            return;
        }

        result.setClassName(logger);

        if (logger.contains(".")) {
            int lastDotIndex = logger.lastIndexOf('.');
            String simpleClassName = logger.substring(lastDotIndex + 1);
            String packageName = logger.substring(0, lastDotIndex);

            result.setMethodName(simpleClassName);
            result.setFileName(packageName);
        } else {
            result.setMethodName(logger);
        }
    }
}
