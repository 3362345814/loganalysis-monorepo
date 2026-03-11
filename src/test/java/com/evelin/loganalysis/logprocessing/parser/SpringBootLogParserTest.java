package com.evelin.loganalysis.logprocessing.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SpringBootLogParserTest {

    private SpringBootLogParser parser;

    @BeforeEach
    void setUp() {
        parser = new SpringBootLogParser();
    }

    @Test
    @DisplayName("测试标准Spring Boot日志解析 - 包含行号")
    void testParseStandardLogWithLineNumber() {
        String log = "2026-03-10 15:37:03.492 [INFO] [scheduling-1] c.e.a.service.LogGeneratorService - Test message";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertNotNull(result.getTimestamp());
        assertEquals("INFO", result.getLevel());
        assertEquals("scheduling-1", result.getThread());
        assertEquals("c.e.a.service.LogGeneratorService", result.getClassName());
        assertEquals("Test message", result.getMessage());
    }

    @Test
    @DisplayName("测试标准Spring Boot日志解析 - 包含行号")
    void testParseLogWithLineNumber() {
        String log = "2026-03-10 15:37:03.492 [INFO] [scheduling-1] c.e.a.service.LogGeneratorService:45 - Test message with line number";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertNotNull(result.getTimestamp());
        assertEquals("INFO", result.getLevel());
        assertEquals("scheduling-1", result.getThread());
        assertEquals("c.e.a.service.LogGeneratorService", result.getClassName());
        assertEquals(45, result.getLineNumber());
        assertEquals("Test message with line number", result.getMessage());
    }

    @Test
    @DisplayName("测试ERROR级别日志解析")
    void testParseErrorLevel() {
        String log = "2026-03-10 11:09:05.006 [ERROR] [scheduling-1] c.e.a.service.LogGeneratorService - 生成日志时发生异常";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("ERROR", result.getLevel());
        assertEquals("scheduling-1", result.getThread());
        assertEquals("c.e.a.service.LogGeneratorService", result.getClassName());
    }

    @Test
    @DisplayName("测试WARN级别日志解析")
    void testParseWarnLevel() {
        String log = "2026-03-10 14:20:37.375 [WARN] [scheduling-1] c.e.a.service.LogGeneratorService - 支付敏感信息";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("WARN", result.getLevel());
    }

    @Test
    @DisplayName("测试DEBUG级别日志解析")
    void testParseDebugLevel() {
        String log = "2026-03-10 11:09:08.011 [DEBUG] [scheduling-1] c.e.a.service.LogGeneratorService - 调试信息";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("DEBUG", result.getLevel());
    }

    @Test
    @DisplayName("测试TRACE级别日志解析")
    void testParseTraceLevel() {
        String log = "2026-03-10 10:00:00.000 [TRACE] [main] c.e.a.service.LogGeneratorService - Trace message";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("TRACE", result.getLevel());
    }

    @Test
    @DisplayName("测试长线程名解析 - RMI TCP Connection")
    void testParseLongThreadName() {
        String log = "2026-03-10 11:08:57.092 [RMI TCP Connection(2)-127.0.0.1] INFO  o.a.catalina.core.StandardService - Starting service";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("RMI TCP Connection(2)-127.0.0.1", result.getThread());
    }

    @Test
    @DisplayName("测试http-nio线程名解析")
    void testParseHttpNioThreadName() {
        String log = "2026-03-10 10:00:00.000 [INFO] [http-nio-8080-exec-1] c.e.a.controller.TestController - Request received";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("http-nio-8080-exec-1", result.getThread());
    }

    @Test
    @DisplayName("测试main线程名解析")
    void testParseMainThreadName() {
        String log = "2026-03-10 11:08:56.322 [main] INFO  c.example.autolog.AutologApplication - Starting Application";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("main", result.getThread());
    }

    @Test
    @DisplayName("测试完整包名和类名分离 - 多层包名")
    void testParseFullPackageAndClass() {
        String log = "2026-03-10 15:37:03.492 [INFO] [scheduling-1] c.e.a.service.LogGeneratorService - Test message";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("c.e.a.service.LogGeneratorService", result.getClassName());
        assertEquals("LogGeneratorService", result.getMethodName());
        assertEquals("c.e.a.service", result.getFileName());
    }

    @Test
    @DisplayName("测试简单类名解析 - 无包名")
    void testParseSimpleClassName() {
        String log = "2026-03-10 15:37:03.492 [INFO] [scheduling-1] MyClass - Test message";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("MyClass", result.getClassName());
        assertEquals("MyClass", result.getMethodName());
        assertEquals("", result.getFileName());
    }

    @Test
    @DisplayName("测试6位毫秒时间解析")
    void testParseTimestampWith6Digits() {
        String log = "2026-03-10 15:37:03.492123 [INFO] [scheduling-1] c.e.a.service.LogGeneratorService - Test message";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertNotNull(result.getTimestamp());
    }

    @Test
    @DisplayName("测试包含异常信息的日志解析")
    void testParseLogWithException() {
        String log = "2026-03-10 11:37:02.315 [scheduling-1] ERROR c.e.a.service.LogGeneratorService - 订单处理失败 - orderId: ORD1773113819310, error: 超过购买限制, stackTrace: java.lang.RuntimeException";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("ERROR", result.getLevel());
        assertEquals("RuntimeException", result.getExceptionType());
    }

    @Test
    @DisplayName("测试空内容解析")
    void testParseEmptyContent() {
        ParseResult result = parser.parse("");

        assertFalse(result.isSuccess());
        assertEquals("Empty content", result.getErrorMessage());
    }

    @Test
    @DisplayName("测试null内容解析")
    void testParseNullContent() {
        ParseResult result = parser.parse(null);

        assertFalse(result.isSuccess());
        assertEquals("Empty content", result.getErrorMessage());
    }

    @Test
    @DisplayName("测试supports方法 - 正确格式")
    void testSupportsValidFormat() {
        String validLog = "2026-03-10 15:37:03.492 [INFO] [scheduling-1] c.e.a.service.LogGeneratorService - Test message";

        assertTrue(parser.supports(validLog));
    }

    @Test
    @DisplayName("测试supports方法 - 错误格式")
    void testSupportsInvalidFormat() {
        String invalidLog = "This is not a valid Spring Boot log";

        assertFalse(parser.supports(invalidLog));
    }

    @Test
    @DisplayName("测试真实日志样本 - 订单创建")
    void testParseRealLogOrderCreated() {
        String log = "2026-03-10 11:09:20.030 [scheduling-1] INFO  c.e.a.service.LogGeneratorService - 订单创建成功 - orderId: ORD1773112160025, userId: user_10005, amount: 4101.00";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("INFO", result.getLevel());
        assertEquals("scheduling-1", result.getThread());
        assertEquals("c.e.a.service.LogGeneratorService", result.getClassName());
        assertEquals("c.e.a.service", result.getFileName());
        assertEquals("LogGeneratorService", result.getMethodName());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("订单创建成功"));
    }

    @Test
    @DisplayName("测试真实日志样本 - 数据查询")
    void testParseRealLogDataQuery() {
        String log = "2026-03-10 11:09:11.015 [scheduling-1] INFO  c.e.a.service.LogGeneratorService - 数据查询 - userId: user_10002, 查询条件: email=test@company.org";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("INFO", result.getLevel());
        assertEquals("scheduling-1", result.getThread());
        assertEquals("c.e.a.service.LogGeneratorService", result.getClassName());
        assertEquals("c.e.a.service", result.getFileName());
    }

    @Test
    @DisplayName("测试supports方法 - 空内容")
    void testSupportsEmptyContent() {
        assertFalse(parser.supports(""));
        assertFalse(parser.supports(null));
    }

    @Test
    @DisplayName("测试spring framework类名解析")
    void testParseSpringFrameworkClass() {
        String log = "2026-03-10 11:08:56.750 [main] INFO  o.s.b.w.s.c.ServletWebServerApplicationContext - Root WebApplicationContext";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("o.s.b.w.s.c.ServletWebServerApplicationContext", result.getClassName());
        assertEquals("ServletWebServerApplicationContext", result.getMethodName());
        assertEquals("o.s.b.w.s.c", result.getFileName());
    }

    @Test
    @DisplayName("测试tomcat类名解析")
    void testParseTomcatClass() {
        String log = "2026-03-10 11:08:56.730 [main] INFO  o.a.catalina.core.StandardService - Starting service [Tomcat]";

        ParseResult result = parser.parse(log);

        assertTrue(result.isSuccess());
        assertEquals("o.a.catalina.core.StandardService", result.getClassName());
        assertEquals("StandardService", result.getMethodName());
        assertEquals("o.a.catalina.core", result.getFileName());
    }
}
