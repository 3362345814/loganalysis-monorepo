package com.evelin.loganalysis.logprocessing.parser.token;

/**
 * 字面量 Token
 * 表示格式字符串中的普通文本（非占位符部分）
 *
 * @author Evelin
 */
public class LiteralToken extends Token {

    private final String text;

    public LiteralToken(String text) {
        this.text = text;
        this.type = "LITERAL";
    }

    public String getText() {
        return text;
    }

    @Override
    public String toRegex() {
        if (text == null || text.isEmpty()) {
            return "";
        }
        // 需要转义正则特殊字符
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if ("\\[]{}()*+?.,^$|# ".indexOf(c) >= 0) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public String getFieldName() {
        return null;
    }
}
