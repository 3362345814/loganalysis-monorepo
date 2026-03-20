package com.evelin.loganalysis.logprocessing.parser.context;

/**
 * 解析上下文
 * 基于 delimiter 的流式解析器
 *
 * 核心逻辑：
 * 1. 从当前光标位置开始
 * 2. 使用 endDelimiter 定位内容边界
 * 3. 返回边界内的内容，并移动光标到 delimiter 之后
 *
 * @author Evelin
 */
public class ParseContext {

    private final String log;
    private int cursor;

    public ParseContext(String log) {
        this.log = log != null ? log : "";
        this.cursor = 0;
    }

    /**
     * 读取直到指定 delimiter
     * 如果 delimiter 为 null，读取剩余所有内容
     *
     * @param delimiter 结束分隔符
     * @return delimiter 之前的内容（不含 delimiter）
     */
    public String readUntil(String delimiter) {
        if (delimiter == null) {
            return readRemaining();
        }

        int idx = log.indexOf(delimiter, cursor);
        if (idx == -1) {
            // 没找到 delimiter，读取剩余
            return readRemaining();
        }

        String result = log.substring(cursor, idx);
        cursor = idx + delimiter.length();
        return result;
    }

    /**
     * 读取直到匹配指定的正则表达式模式
     * 用于 DateToken 等需要根据模式确定边界的场景
     *
     * @param regex 用于匹配的正则表达式（不加捕获组）
     * @return 匹配到的内容
     */
    public String readUntilPattern(String regex) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(log.substring(cursor));

        if (matcher.find()) {
            String matched = matcher.group();
            cursor += matched.length();
            return matched;
        }

        // 没找到，读取剩余
        return readRemaining();
    }

    /**
     * 读取剩余所有内容
     */
    public String readRemaining() {
        if (cursor >= log.length()) {
            return "";
        }
        String result = log.substring(cursor);
        cursor = log.length();
        return result;
    }

    /**
     * 跳过指定的 literal 文本
     * 用于精确匹配格式中的固定字符（如方括号）
     *
     * @param literal 要跳过的文本
     * @return 是否成功跳过
     */
    public boolean skipLiteral(String literal) {
        if (literal == null || literal.isEmpty()) {
            return true;
        }

        if (cursor + literal.length() > log.length()) {
            return false;
        }

        if (log.substring(cursor, cursor + literal.length()).equals(literal)) {
            cursor += literal.length();
            return true;
        }
        return false;
    }

    /**
     * 尝试跳过空白字符
     */
    public void skipWhitespace() {
        while (cursor < log.length() && Character.isWhitespace(log.charAt(cursor))) {
            cursor++;
        }
    }

    /**
     * 检查是否已到达末尾
     */
    public boolean isEnd() {
        return cursor >= log.length();
    }

    /**
     * 获取当前光标位置
     */
    public int getCursor() {
        return cursor;
    }

    /**
     * 设置光标位置
     */
    public void setCursor(int cursor) {
        this.cursor = Math.max(0, Math.min(cursor, log.length()));
    }

    /**
     * 获取从当前位置开始的剩余字符串（用于调试）
     */
    public String peekRemaining() {
        if (cursor >= log.length()) {
            return "";
        }
        return log.substring(cursor);
    }

    /**
     * 精确读取直到指定 delimiter（不跳过任何字符）
     * 与 readUntil 的区别：会先精确匹配 leading literal
     *
     * 例如：格式 [%thread] 的 leading 是 "["
     *       读取时先确保跳过 "["，然后再找 "]"
     */
    public String readUntilWithLeading(String leading, String delimiter) {
        // 先跳过 leading
        if (leading != null && !leading.isEmpty()) {
            skipLiteral(leading);
        }

        // 再找 delimiter
        return readUntil(delimiter);
    }

    /**
     * 移除字符串两端的指定字符
     */
    public static String strip(String value, String left, String right) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String result = value;
        if (left != null && !left.isEmpty() && result.startsWith(left)) {
            result = result.substring(left.length());
        }
        if (right != null && !right.isEmpty() && result.endsWith(right)) {
            result = result.substring(0, result.length() - right.length());
        }
        return result;
    }

    /**
     * 移除字符串两端的空白字符
     */
    public static String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    /**
     * 获取原始日志内容（用于调试）
     */
    public String getOriginalLog() {
        return log;
    }

    @Override
    public String toString() {
        return "ParseContext{" +
                "cursor=" + cursor +
                ", remaining='" + peekRemaining() + '\'' +
                '}';
    }
}
