package com.evelin.loganalysis.logprocessing.parser.token;

import com.evelin.loganalysis.logprocessing.parser.ParseResult;
import com.evelin.loganalysis.logprocessing.parser.context.ParseContext;

/**
 * 线程 Token
 * 格式: %thread
 *
 * 线程名通常被方括号包围，如 [http-nio-8080-exec-1]
 * 解析时会自动去除方括号
 *
 * @author Evelin
 */
public class ThreadToken extends Token {

    public ThreadToken() {
        this.type = "THREAD";
    }

    @Override
    public String toRegex() {
        // 线程名通常为 [] 包裹或不含空格的字符串
        return "(\\[?[^\\]\\s]+\\]?)";
    }

    @Override
    public String getFieldName() {
        return "thread";
    }

    @Override
    public void parse(ParseContext context, ParseResult result) {
        String rawValue = context.readUntil(endDelimiter);

        // 跳过 endDelimiter
        if (endDelimiter != null) {
            context.skipLiteral(endDelimiter);
        }

        // 去除方括号
        String threadName = stripBrackets(rawValue);

        // 填充结果
        result.getFields().put(getFieldName(), threadName);
        result.setThread(threadName);
    }

    /**
     * 去除字符串两端的方括号
     * 例如：[http-nio-8080-exec-1] -> http-nio-8080-exec-1
     */
    private String stripBrackets(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String result = value.trim();

        // 去除左方括号
        if (result.startsWith("[")) {
            result = result.substring(1);
        }

        // 去除右方括号
        if (result.endsWith("]")) {
            result = result.substring(0, result.length() - 1);
        }

        return result.trim();
    }
}
