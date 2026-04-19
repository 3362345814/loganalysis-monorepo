package com.evelin.loganalysis.logcollection.service;

import com.evelin.loganalysis.logcollection.dto.Log4jExtractTestRequest;
import com.evelin.loganalysis.logcollection.dto.Log4jExtractTestResponse;
import com.evelin.loganalysis.logcommon.constant.ResultCode;
import com.evelin.loganalysis.logcommon.exception.BusinessException;
import com.evelin.loganalysis.logprocessing.parser.Log4jLogParser;
import com.evelin.loganalysis.logprocessing.parser.ParseResult;
import com.evelin.loganalysis.logprocessing.parser.token.DateToken;
import com.evelin.loganalysis.logprocessing.parser.token.PatternTokenizer;
import com.evelin.loganalysis.logprocessing.parser.token.Token;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class Log4jExtractTestService {

    private final Log4jLogParser log4jLogParser;

    public Log4jExtractTestResponse testExtract(Log4jExtractTestRequest request) {
        String pattern = normalizePattern(request.getPattern());
        String sampleLog = normalizeSample(request.getSampleLog());
        String traceFieldName = normalizeTraceFieldName(request.getTraceFieldName());

        validatePattern(pattern);

        ParseResult parseResult = log4jLogParser.parse(sampleLog, pattern);
        Map<String, Object> fields = parseResult.getFields();
        String traceId = extractTraceId(fields, traceFieldName);
        boolean matched = isMatched(parseResult, sampleLog);

        return Log4jExtractTestResponse.builder()
                .matched(matched)
                .message(matched ? "提取成功" : "日志与格式不匹配")
                .logTime(parseResult.getTimestamp() != null ? parseResult.getTimestamp().toString() : null)
                .logLevel(parseResult.getLevel())
                .threadName(parseResult.getThread())
                .loggerName(parseResult.getLogger())
                .content(parseResult.getMessage())
                .traceId(traceId)
                .traceFieldName(traceFieldName)
                .stackTrace(parseResult.getStackTrace())
                .parsedFields(fields)
                .build();
    }

    private void validatePattern(String pattern) {
        if (!pattern.contains("%msg") && !pattern.contains("%m")) {
            throw new BusinessException(ResultCode.RULE_VALIDATION_ERROR, "日志格式必须包含 %msg 或 %m");
        }
        try {
            PatternTokenizer tokenizer = new PatternTokenizer();
            List<Token> tokens = tokenizer.tokenize(pattern);
            if (tokens.isEmpty()) {
                throw new BusinessException(ResultCode.RULE_VALIDATION_ERROR, "日志格式无法解析，请检查占位符写法");
            }
            boolean hasDateToken = tokens.stream().anyMatch(token -> token instanceof DateToken);
            if (!hasDateToken) {
                throw new BusinessException(ResultCode.RULE_VALIDATION_ERROR, "日志格式必须包含时间占位符 %d{...}");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.RULE_VALIDATION_ERROR, "日志格式中的日期模板无效: " + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.RULE_VALIDATION_ERROR, "日志格式校验失败: " + e.getMessage());
        }
    }

    private boolean isMatched(ParseResult parseResult, String sampleLog) {
        if (parseResult == null || parseResult.getMessage() == null) {
            return false;
        }
        String normalizedRaw = normalizeLine(sampleLog);
        String normalizedParsed = normalizeLine(parseResult.getMessage());
        if (!normalizedRaw.equals(normalizedParsed)) {
            return true;
        }
        return StringUtils.hasText(parseResult.getThread())
                || StringUtils.hasText(parseResult.getLogger())
                || StringUtils.hasText(parseResult.getExceptionType());
    }

    private String normalizePattern(String pattern) {
        return pattern == null ? "" : pattern.trim();
    }

    private String normalizeSample(String sample) {
        if (sample == null) {
            return "";
        }
        String normalized = sample.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.strip();
    }

    private String normalizeLine(String value) {
        if (value == null) {
            return "";
        }
        int lineBreak = value.indexOf('\n');
        String firstLine = lineBreak >= 0 ? value.substring(0, lineBreak) : value;
        return firstLine.trim();
    }

    private String extractTraceId(Map<String, Object> fields, String traceFieldName) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        if (traceFieldName == null) {
            return null;
        }

        Object value = fields.get(traceFieldName);
        if (value != null) {
            return String.valueOf(value);
        }
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(traceFieldName)) {
                return String.valueOf(entry.getValue());
            }
        }
        return null;
    }

    private String normalizeTraceFieldName(String traceFieldName) {
        if (traceFieldName == null) {
            return null;
        }
        String value = traceFieldName.trim();
        return value.isEmpty() ? null : value;
    }
}
