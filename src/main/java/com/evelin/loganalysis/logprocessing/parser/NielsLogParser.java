package com.evelin.loganalysis.logprocessing.parser;

import lombok.extern.slf4j.Slf4j;
import nl.basjes.parse.httpdlog.HttpdLoglineParser;
import nl.basjes.parse.core.Field;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 基于 nielsbasjes/logparser 的 Nginx 访问日志解析器
 * 支持自定义 log_format，与 Nginx 配置完全兼容
 *
 * @author Evelin
 */
@Slf4j
@Component
public class NielsLogParser implements ParseStrategy {

    private HttpdLoglineParser<NginxAccessLog> parser;
    private String configuredFormat;

    /**
     * Nginx 访问日志 POJO
     */
    public static class NginxAccessLog {
        // 连接信息
        private String clientIp;
        private String clientHost;
        private String clientUser;

        // 时间
        private String requestTime;
        private String requestTimeDay;
        private String requestTimeMonth;
        private String requestTimeMonthName;
        private String requestTimeYear;
        private String requestTimeHour;
        private String requestTimeMinute;
        private String requestTimeSecond;
        private String requestTimeMillisecond;
        private String requestTimeTimezone;

        // 请求信息
        private String requestFirstLine;
        private String requestMethod;
        private String requestUri;
        private String requestQueryString;
        private String requestProtocol;
        private String requestProtocolVersion;

        // 响应信息
        private String status;
        private String responseBodyBytes;
        private String responseBodyBytesCLF;

        // 请求头
        private String referer;
        private String userAgent;

        // Cookie
        private Map<String, String> cookies = new HashMap<>();

        // Getters and Setters
        @Field("IP:connection.client.host")
        public void setClientIp(String clientIp) {
            this.clientIp = clientIp;
        }

        @Field("STRING:connection.client.logname")
        public void setClientHost(String clientHost) {
            this.clientHost = clientHost;
        }

        @Field("STRING:connection.client.user")
        public void setClientUser(String clientUser) {
            this.clientUser = clientUser;
        }

        @Field("TIME.STAMP:request.receive.time")
        public void setRequestTime(String requestTime) {
            this.requestTime = requestTime;
        }

        @Field("TIME.DAY:request.receive.time.day")
        public void setRequestTimeDay(String requestTimeDay) {
            this.requestTimeDay = requestTimeDay;
        }

        @Field("TIME.MONTH:request.receive.time.month")
        public void setRequestTimeMonth(String requestTimeMonth) {
            this.requestTimeMonth = requestTimeMonth;
        }

        @Field("TIME.MONTHNAME:request.receive.time.monthname")
        public void setRequestTimeMonthName(String requestTimeMonthName) {
            this.requestTimeMonthName = requestTimeMonthName;
        }

        @Field("TIME.YEAR:request.receive.time.year")
        public void setRequestTimeYear(String requestTimeYear) {
            this.requestTimeYear = requestTimeYear;
        }

        @Field("TIME.HOUR:request.receive.time.hour")
        public void setRequestTimeHour(String requestTimeHour) {
            this.requestTimeHour = requestTimeHour;
        }

        @Field("TIME.MINUTE:request.receive.time.minute")
        public void setRequestTimeMinute(String requestTimeMinute) {
            this.requestTimeMinute = requestTimeMinute;
        }

        @Field("TIME.SECOND:request.receive.time.second")
        public void setRequestTimeSecond(String requestTimeSecond) {
            this.requestTimeSecond = requestTimeSecond;
        }

        @Field("TIME.MILLISECOND:request.receive.time.millisecond")
        public void setRequestTimeMillisecond(String requestTimeMillisecond) {
            this.requestTimeMillisecond = requestTimeMillisecond;
        }

        @Field("TIME.ZONE:request.receive.time.timezone")
        public void setRequestTimeTimezone(String requestTimeTimezone) {
            this.requestTimeTimezone = requestTimeTimezone;
        }

        @Field("HTTP.FIRSTLINE:request.firstline")
        public void setRequestFirstLine(String requestFirstLine) {
            this.requestFirstLine = requestFirstLine;
        }

        @Field("HTTP.METHOD:request.firstline.method")
        public void setRequestMethod(String requestMethod) {
            this.requestMethod = requestMethod;
        }

        @Field("HTTP.URI:request.firstline.uri")
        public void setRequestUri(String requestUri) {
            this.requestUri = requestUri;
        }

        @Field("HTTP.QUERYSTRING:request.firstline.uri.query")
        public void setRequestQueryString(String requestQueryString) {
            this.requestQueryString = requestQueryString;
        }

        @Field("HTTP.PROTOCOL:request.firstline.protocol")
        public void setRequestProtocol(String requestProtocol) {
            this.requestProtocol = requestProtocol;
        }

        @Field("HTTP.PROTOCOL.VERSION:request.firstline.protocol.version")
        public void setRequestProtocolVersion(String requestProtocolVersion) {
            this.requestProtocolVersion = requestProtocolVersion;
        }

        @Field("HTTP.CODE:request.status.last")
        public void setStatus(String status) {
            this.status = status;
        }

        @Field("BYTES:response.body.bytes")
        public void setResponseBodyBytes(String responseBodyBytes) {
            this.responseBodyBytes = responseBodyBytes;
        }

        @Field("BYTESCLF:response.body.bytes.clf")
        public void setResponseBodyBytesCLF(String responseBodyBytesCLF) {
            this.responseBodyBytesCLF = responseBodyBytesCLF;
        }

        @Field("HTTP.URI:request.referer")
        public void setReferer(String referer) {
            this.referer = referer;
        }

        @Field("HTTP.USERAGENT:request.user-agent")
        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        @Field("HTTP.COOKIE:request.cookies.*")
        public void setCookie(String name, String value) {
            this.cookies.put(name, value);
        }

        // Convert to Map
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            if (clientIp != null) map.put("client_ip", clientIp);
            if (clientHost != null) map.put("client_host", clientHost);
            if (clientUser != null) map.put("client_user", clientUser);
            if (requestTime != null) map.put("request_time", requestTime);
            if (requestTimeDay != null) map.put("request_time_day", requestTimeDay);
            if (requestTimeMonth != null) map.put("request_time_month", requestTimeMonth);
            if (requestTimeMonthName != null) map.put("request_time_month_name", requestTimeMonthName);
            if (requestTimeYear != null) map.put("request_time_year", requestTimeYear);
            if (requestTimeHour != null) map.put("request_time_hour", requestTimeHour);
            if (requestTimeMinute != null) map.put("request_time_minute", requestTimeMinute);
            if (requestTimeSecond != null) map.put("request_time_second", requestTimeSecond);
            if (requestTimeMillisecond != null) map.put("request_time_millisecond", requestTimeMillisecond);
            if (requestTimeTimezone != null) map.put("request_time_timezone", requestTimeTimezone);
            if (requestFirstLine != null) map.put("request_firstline", requestFirstLine);
            if (requestMethod != null) map.put("request_method", requestMethod);
            if (requestUri != null) map.put("request_uri", requestUri);
            if (requestQueryString != null) map.put("request_query_string", requestQueryString);
            if (requestProtocol != null) map.put("request_protocol", requestProtocol);
            if (requestProtocolVersion != null) map.put("request_protocol_version", requestProtocolVersion);
            if (status != null) map.put("status", status);
            if (responseBodyBytes != null) map.put("body_bytes_sent", responseBodyBytes);
            if (referer != null) map.put("http_referer", referer);
            if (userAgent != null) map.put("http_user_agent", userAgent);
            if (!cookies.isEmpty()) map.put("cookies", cookies);
            return map;
        }
    }

    /**
     * 配置 log_format
     */
    public void configure(String logFormat) {
        if (logFormat == null || logFormat.isEmpty()) {
            throw new IllegalArgumentException("logFormat cannot be empty");
        }

        // 如果格式相同，不需要重新创建
        if (logFormat.equals(configuredFormat) && parser != null) {
            return;
        }

        log.debug("Configuring NielsLogParser with format: {}", logFormat);

        try {
            // 直接使用 Nginx log_format，不需要转换
            // nielsbasjes/logparser 原生支持 Nginx 格式
            this.parser = new HttpdLoglineParser<>(NginxAccessLog.class, logFormat);
            this.configuredFormat = logFormat;
            log.info("NielsLogParser configured successfully with format: {}", logFormat);

        } catch (Exception e) {
            log.error("Failed to configure NielsLogParser: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Invalid log_format: " + e.getMessage());
        }
    }

    /**
     * 直接使用 Nginx log_format
     * nielsbasjes/logparser 原生支持 Nginx 格式，不需要转换
     */
    private String convertNginxToApacheFormat(String nginxFormat) {
        // 不再需要转换，直接返回原始格式
        return nginxFormat;
    }

    @Override
    public ParseResult parse(String content) {
        if (content == null || content.isEmpty()) {
            return ParseResult.builder()
                    .success(false)
                    .errorMessage("Empty content")
                    .build();
        }

        // 如果未配置，使用默认格式
        if (parser == null) {
            configure(getDefaultLogFormat());
        }

        try {
            // 解析日志行
            NginxAccessLog parsedLog = parser.parse(content);

            // 调试：打印 nielsbasjes 实际解析出的所有字段
            Map<String, Object> allFields = parsedLog.toMap();
            log.debug("Niels raw parsed fields: {}", allFields);

            // 转换为 Map
            Map<String, Object> fields = parsedLog.toMap();

            if (!fields.isEmpty()) {
                log.debug("Parsed fields: {}", fields.keySet());
                return buildParseResult(fields);
            } else {
                log.warn("NielsLogParser: no fields parsed, content: {}", content);
            }
        } catch (Exception e) {
            log.warn("Failed to parse with NielsLogParser: {}, content: {}", e.getMessage(), content);
        }

        // 无法解析，返回原始内容
        return parseFallback(content);
    }

    private ParseResult buildParseResult(Map<String, Object> fields) {
        return ParseResult.builder()
                .success(true)
                .logType("nginx_access")
                .fields(fields)
                .build();
    }

    @Override
    public boolean supports(String content) {
        if (content == null || content.isEmpty() || parser == null) {
            return false;
        }

        try {
            NginxAccessLog parsedLog = parser.parse(content);
            return parsedLog != null && !parsedLog.toMap().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getFormatName() {
        return "NGINX_ACCESS_NIELS";
    }

    /**
     * 获取默认的 log_format (Nginx combined)
     */
    public static String getDefaultLogFormat() {
        return "$remote_addr - $remote_user [$time_local] \"$request\" $status $body_bytes_sent \"$http_referer\" \"$http_user_agent\"";
    }

    /**
     * 获取项目使用的完整 log_format
     */
    public static String getProjectLogFormat() {
        return "$remote_addr - $remote_user [$time_local] \"$request\" $status $body_bytes_sent \"$http_referer\" \"$http_user_agent\" \"$http_x_forwarded_for\" rt=$request_time uct=\"$upstream_connect_time\" uht=\"$upstream_header_time\" urt=\"$upstream_response_time\"";
    }

    /**
     * Fallback: 返回原始内容
     */
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
