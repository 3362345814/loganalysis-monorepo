package com.evelin.loganalysis.logprocessing.parser.token;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志格式字符串分词器
 * 将 Log4j/Logback 格式字符串（如 "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"）
 * 转换为 Token 列表，并自动推导 endDelimiter 和 leadingDelimiter
 *
 * 算法简化版：
 * 1. 不创建 LiteralToken
 * 2. literal 文本直接累积到前一个 Token 的 endDelimiter
 * 3. %n 解析为换行符
 *
 * @author Evelin
 */
@Slf4j
public class PatternTokenizer {

    /**
     * 分词入口
     *
     * 算法简化版：
     * 1. 不创建 LiteralToken
     * 2. literal 文本直接累积到前一个 Token 的 endDelimiter
     * 3. 如果 literal 以 [({ 开头，这部分属于下一个 token 的 delimiter
     *
     * @param pattern 日志格式字符串
     * @return Token 列表
     */
    public List<Token> tokenize(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return new ArrayList<>();
        }

        List<Token> tokens = new ArrayList<>();
        StringBuilder pendingLiteral = new StringBuilder();

        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);

            if (c == '%') {
                // 先处理 pending literal
                if (pendingLiteral.length() > 0) {
                    processPendingLiteral(tokens, pendingLiteral.toString());
                    pendingLiteral.setLength(0);
                }

                // 解析 token
                int nextIndex = parseToken(pattern, i, tokens);
                i = nextIndex;
            } else {
                pendingLiteral.append(c);
                i++;
            }
        }

        // 处理最后的 literal
        if (pendingLiteral.length() > 0) {
            processPendingLiteral(tokens, pendingLiteral.toString());
        }

        return tokens;
    }

    /**
     * 处理 pending literal
     * - 找出 leading delimiter（如 [）(属于下一个 token）
     * - 剩余部分作为前一个 token 的 endDelimiter
     */
    private void processPendingLiteral(List<Token> tokens, String literal) {
        if (tokens.isEmpty()) {
            // pattern 以 literal 开头，忽略
            return;
        }

        Token lastToken = tokens.get(tokens.size() - 1);

        // 找 leading delimiter
        // - 跳过开头的空白
        // - 如果遇到 [({，这是下一个 token 的 leading
        int pos = 0;
        while (pos < literal.length() && Character.isWhitespace(literal.charAt(pos))) {
            pos++;
        }

        if (pos < literal.length()) {
            char c = literal.charAt(pos);
            if (c == '[' || c == '(' || c == '{') {
                // 这是下一个 token 的 leading delimiter
                // 之前的部分（空白）作为 lastToken 的 endDelimiter
                if (pos > 0) {
                    lastToken.setEndDelimiter(literal.substring(0, pos));
                }

                // 创建 leading info，存储到下一个 token
                // 找对应的 end delimiter
                char endChar = getMatchingEnd(c);
                int endPos = literal.indexOf(endChar, pos + 1);
                if (endPos > pos) {
                    // 找到闭合字符，存储 leading/end info
                    // 例如: "[thread]" -> leading="[", end="]"
                    lastToken.setNextTokenDelimiter(
                            String.valueOf(c),
                            literal.substring(pos + 1, endPos)
                    );
                } else {
                    // 没找到闭合，只有 leading
                    lastToken.setNextTokenDelimiter(String.valueOf(c), null);
                }
                return;
            }
        }

        // 没有 leading delimiter，整个 literal 作为 endDelimiter
        lastToken.setEndDelimiter(literal);
    }

    private char getMatchingEnd(char start) {
        switch (start) {
            case '[': return ']';
            case '(': return ')';
            case '{': return '}';
            default: return start;
        }
    }

    /**
     * 解析单个 Token
     */
    private int parseToken(String pattern, int start, List<Token> tokens) {
        int i = start + 1;

        if (i >= pattern.length()) {
            // 单独的 % → 忽略
            return i;
        }

        // 处理 %n（换行符）
        if (pattern.charAt(i) == 'n') {
            Token token = new NewLineToken();
            token.setEndDelimiter("\n");
            tokens.add(token);
            return i + 1;
        }

        // 处理对齐标志
        boolean leftAlign = false;
        if (pattern.charAt(i) == '-') {
            leftAlign = true;
            i++;
        }

        // 解析宽度
        int width = -1;
        int numStart = i;
        while (i < pattern.length() && Character.isDigit(pattern.charAt(i))) {
            i++;
        }
        if (i > numStart) {
            try {
                width = Integer.parseInt(pattern.substring(numStart, i));
            } catch (NumberFormatException e) {
                // 忽略
            }
        }

        // 读取 token 名称
        int nameStart = i;
        while (i < pattern.length() && Character.isLetter(pattern.charAt(i))) {
            i++;
        }
        String name = pattern.substring(nameStart, i);

        // 处理 {} 参数
        String param = null;
        if (i < pattern.length() && pattern.charAt(i) == '{') {
            int braceStart = i + 1;
            int braceEnd = findClosingBrace(pattern, braceStart);
            if (braceEnd > braceStart) {
                param = pattern.substring(braceStart, braceEnd);
                i = braceEnd + 1;
            } else {
                i++;
            }
        }

        Token token = createToken(name, param, width, leftAlign);

        // 检查是否有预先存储的 next token delimiter
        if (!tokens.isEmpty()) {
            Token prevToken = tokens.get(tokens.size() - 1);
            if (prevToken.hasNextTokenDelimiter()) {
                String[] delim = prevToken.getNextTokenDelimiter();
                token.setLeadingDelimiter(delim[0]);
                if (delim[1] != null) {
                    token.setEndDelimiter(delim[1]);
                }
            }
        }

        tokens.add(token);
        return i;
    }

    /**
     * 查找匹配的闭合大括号
     */
    private int findClosingBrace(String pattern, int start) {
        int depth = 1;
        for (int i = start; i < pattern.length(); i++) {
            if (pattern.charAt(i) == '{') {
                depth++;
            } else if (pattern.charAt(i) == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 根据名称创建对应的 Token
     */
    private Token createToken(String name, String param, int width, boolean leftAlign) {
        switch (name) {
            case "d":
            case "date":
                return new DateToken(param);

            case "thread":
            case "t":
                return new ThreadToken();

            case "X":
            case "mdc":
                return new MdcToken(param);

            case "level":
            case "p":
            case "le":
                if (width > 0) {
                    return new LevelToken(width, leftAlign);
                }
                return new LevelToken(-1);

            case "logger":
            case "c":
                int length = -1;
                if (param != null && !param.isEmpty()) {
                    try {
                        length = Integer.parseInt(param);
                    } catch (NumberFormatException e) {
                        // 忽略
                    }
                }
                return new LoggerToken(length);

            case "msg":
            case "m":
                return new MessageToken();

            case "n":
                return new NewLineToken();

            default:
                log.warn("Unknown token pattern: {}", name);
                return new MessageToken();
        }
    }
}