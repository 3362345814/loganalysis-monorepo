package com.evelin.loganalysis.logprocessing.parser.token;

import com.evelin.loganalysis.logprocessing.parser.ParseResult;
import com.evelin.loganalysis.logprocessing.parser.context.ParseContext;

/**
 * 换行 Token
 * 格式: %n
 *
 * @author Evelin
 */
public class NewLineToken extends Token {

    public NewLineToken() {
        this.type = "NEWLINE";
    }

    @Override
    public String toRegex() {
        // 换行符
        return "(\\r?\\n)?";
    }

    @Override
    public String getFieldName() {
        return null;
    }

    @Override
    public void parse(ParseContext context, ParseResult result) {
        // 跳过换行符
        String remaining = context.peekRemaining();
        if (remaining != null) {
            if (remaining.startsWith("\r\n")) {
                context.setCursor(context.getCursor() + 2);
            } else if (remaining.startsWith("\n")) {
                context.setCursor(context.getCursor() + 1);
            }
        }
    }
}
