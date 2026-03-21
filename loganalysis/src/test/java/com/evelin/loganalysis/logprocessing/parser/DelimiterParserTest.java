package com.evelin.loganalysis.logprocessing.parser;

import com.evelin.loganalysis.logprocessing.parser.context.ParseContext;
import com.evelin.loganalysis.logprocessing.parser.token.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 基于 Delimiter 的日志解析器测试
 */
public class DelimiterParserTest {

    @Test
    void testPatternTokenization() {
        String pattern = "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n";

        PatternTokenizer tokenizer = new PatternTokenizer();
        List<Token> tokens = tokenizer.tokenize(pattern);

        System.out.println("=== Pattern Tokenization ===");
        for (Token token : tokens) {
            System.out.println(token.getClass().getSimpleName() +
                    " | endDelimiter=" + token.getEndDelimiter() +
                    " | leadingDelimiter=" + token.getLeadingDelimiter());
        }

        System.out.println("Non-literal tokens: " + tokens.size());

        assertTrue(tokens.size() > 0, "Should have tokens");

        // 验证关键 token
        boolean foundThread = false;
        boolean foundDate = false;
        boolean foundLevel = false;
        boolean foundLogger = false;
        boolean foundMsg = false;

        for (Token token : tokens) {
            if (token instanceof DateToken) {
                foundDate = true;
                // DateToken 使用 format 解析，endDelimiter 只是后续分隔符
                assertEquals(" ", token.getEndDelimiter());
            } else if (token instanceof ThreadToken) {
                foundThread = true;
                // ThreadToken 后面是 "] "
                assertEquals("] ", token.getEndDelimiter());
                assertEquals("[", token.getLeadingDelimiter());
            } else if (token instanceof LevelToken) {
                foundLevel = true;
                // LevelToken 后面是 " "（两个空格）
                assertEquals(" ", token.getEndDelimiter());
            } else if (token instanceof LoggerToken) {
                foundLogger = true;
                // LoggerToken 后面是 " - "（前面有空格）
                assertEquals(" - ", token.getEndDelimiter());
            } else if (token instanceof MessageToken) {
                foundMsg = true;
                // MessageToken 后面是换行符
                assertEquals("\n", token.getEndDelimiter());
            }
        }

        assertTrue(foundDate, "Should have DateToken");
        assertTrue(foundThread, "Should have ThreadToken");
        assertTrue(foundLevel, "Should have LevelToken");
        assertTrue(foundLogger, "Should have LoggerToken");
        assertTrue(foundMsg, "Should have MessageToken");
    }

    @Test
    void testDelimiterParsing() {
        String pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";
        String log = "2026-03-20 12:00:00.123 [main] INFO  com.demo.UserService - login success userId=1";

        PatternTokenizer tokenizer = new PatternTokenizer();
        List<Token> tokens = tokenizer.tokenize(pattern);

        System.out.println("=== Testing Delimiter Parsing ===");
        System.out.println("Pattern: " + pattern);
        System.out.println("Log: " + log);

        ParseContext ctx = new ParseContext(log);
        ParseResult result = ParseResult.builder()
                .success(true)
                .fields(new java.util.HashMap<>())
                .build();

        for (Token token : tokens) {
            System.out.println("Parsing " + token.getClass().getSimpleName() +
                    " (remaining: '" + ctx.peekRemaining() + "')");
            token.parse(ctx, result);
        }

        System.out.println("=== Parse Result ===");
        System.out.println("timestamp: " + result.getTimestamp());
        System.out.println("thread: " + result.getThread());
        System.out.println("level: " + result.getLevel());
        System.out.println("logger: " + result.getLogger());
        System.out.println("message: " + result.getMessage());
        System.out.println("fields: " + result.getFields());

        // 验证解析结果
        assertNotNull(result.getTimestamp());
        assertEquals("main", result.getThread());
        assertEquals("INFO", result.getLevel());
        assertEquals("com.demo.UserService", result.getLogger());
        assertEquals("login success userId=1", result.getMessage());
    }

    @Test
    void testMdcParsing() {
        String pattern = "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n";
        String log = "2026-03-20 12:00:00 [http-nio-8080-exec-1] INFO  com.demo.UserService - login success";

        PatternTokenizer tokenizer = new PatternTokenizer();
        List<Token> tokens = tokenizer.tokenize(pattern);

        System.out.println("=== Testing MDC Parsing ===");
        System.out.println("Pattern: " + pattern);
        System.out.println("Log: " + log);

        ParseContext ctx = new ParseContext(log);
        ParseResult result = ParseResult.builder()
                .success(true)
                .fields(new java.util.HashMap<>())
                .build();

        for (Token token : tokens) {
            System.out.println("Parsing " + token.getClass().getSimpleName() +
                    " (remaining: '" + ctx.peekRemaining() + "')");
            token.parse(ctx, result);
        }

        System.out.println("=== MDC Parse Result ===");
        System.out.println("timestamp: " + result.getTimestamp());
        System.out.println("thread: " + result.getThread());
        System.out.println("all fields: " + result.getFields());

        // 验证基本字段
        assertEquals("http-nio-8080-exec-1", result.getThread());
    }

    @Test
    void testParseContext() {
        // 测试 readUntil
        ParseContext ctx = new ParseContext("hello world test");
        assertEquals("hello", ctx.readUntil(" "));
        assertEquals(6, ctx.getCursor()); // cursor after "hello "
        assertEquals("world", ctx.readUntil(" "));
        assertEquals(12, ctx.getCursor()); // cursor after "world "
        assertEquals("test", ctx.readUntil(null));
        assertTrue(ctx.isEnd());

        // 测试 skipLiteral
        ctx = new ParseContext("[thread] INFO");
        assertTrue(ctx.skipLiteral("["));
        assertEquals(1, ctx.getCursor());
        assertEquals("thread", ctx.readUntil("]"));
        assertTrue(ctx.skipLiteral("] "));
        assertEquals("INFO", ctx.readRemaining());
    }
}
