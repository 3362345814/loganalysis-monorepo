package com.evelin.loganalysis.logprocessing.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class NginxLogParser implements ParseStrategy {

    private static final DateTimeFormatter NGINX_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

    private static final Pattern NGINX_PATTERN = Pattern.compile(
            "^(\\S+)\\s+"                                            // 1. client_ip
            + "\\S+\\s+"                                            // 2. remote_user (ignore)
            + "\\[([^\\]]+)\\]\\s+"                                  // 3. time_local
            + "\"([A-Z]+)\\s+(\\S+)\\s+(\\S+)\"\\s+"               // 4. request_method, 5. request_uri, 6. http_version
            + "(\\d+)\\s+"                                          // 7. status
            + "(\\d+)"                                              // 8. bytes
    );

    @Override
    public ParseResult parse(String content) {
        if (content == null || content.isEmpty()) {
            return ParseResult.builder()
                    .success(false)
                    .errorMessage("Empty content")
                    .build();
        }

        try {
            Matcher matcher = NGINX_PATTERN.matcher(content);

            if (matcher.matches()) {
                log.debug("NginxLogParser: matched, groups: 1={}, 2={}, 3={}, 4={}, 5={}, 6={}, 7={}",
                    matcher.group(1), matcher.group(2), matcher.group(3),
                    matcher.group(4), matcher.group(5), matcher.group(6), matcher.group(7));
                return parseWithMatcher(matcher);
            } else {
                log.warn("NginxLogParser: NOT matched, content: {}", content);
            }
        } catch (Exception e) {
            log.warn("Failed to parse nginx log: {}", e.getMessage());
        }

        return parseFallback(content);
    }

    @Override
    public String getFormatName() {
        return "nginx";
    }

    @Override
    public boolean supports(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        return NGINX_PATTERN.matcher(content).matches();
    }

    private ParseResult parseWithMatcher(Matcher matcher) {
        Map<String, Object> fields = new HashMap<>();

        String clientIp = matcher.group(1);
        String timeLocalStr = matcher.group(2);
        String requestMethod = matcher.group(3);
        String requestUri = matcher.group(4);
        String httpVersion = matcher.group(5);
        String status = matcher.group(6);
        String bytes = matcher.group(7);

        fields.put("client_ip", clientIp);
        fields.put("request_method", requestMethod);
        fields.put("request_uri", requestUri);
        fields.put("http_version", httpVersion);
        fields.put("status", Integer.parseInt(status));
        fields.put("bytes", Integer.parseInt(bytes));
        fields.put("time_local", timeLocalStr);

        try {
            LocalDateTime logTime = LocalDateTime.parse(timeLocalStr, NGINX_FORMATTER);
            fields.put("log_time", logTime);
        } catch (Exception e) {
            log.debug("Failed to parse time: {}", timeLocalStr);
        }

        return ParseResult.builder()
                .success(true)
                .logType("nginx")
                .fields(fields)
                .build();
    }

    private ParseResult parseFallback(String content) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("raw_content", content);
        
        return ParseResult.builder()
                .success(true)
                .logType("unknown")
                .fields(fields)
                .build();
    }
}
