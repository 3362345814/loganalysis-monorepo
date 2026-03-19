package com.evelin.loganalysis.logalert.service;

import com.evelin.loganalysis.logalert.enums.AlertLevel;
import com.evelin.loganalysis.logalert.enums.NotificationChannel;
import com.evelin.loganalysis.logalert.model.AlertNotification;
import com.evelin.loganalysis.logalert.model.AlertRule;
import com.evelin.loganalysis.logalert.model.NotificationChannelConfig;
import com.evelin.loganalysis.logalert.repository.AlertNotificationRepository;
import com.evelin.loganalysis.logalert.repository.NotificationChannelConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.commons.codec.binary.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class DingtalkNotificationService {

    private final AlertNotificationRepository notificationRepository;
    private final NotificationChannelConfigRepository channelConfigRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 1000;

    @Value("${alert.notification.dingtalk.webhook-url:}")
    private String defaultDingtalkWebhookUrl;

    @Value("${alert.notification.dingtalk.secret:}")
    private String defaultDingtalkSecret;

    @Value("${alert.notification.dingtalk.default-recipients:}")
    private String defaultRecipients;

    public void sendDingtalkNotification(AlertRule rule, String title, String content) {
        Map<String, String> config = getChannelConfig(NotificationChannel.DINGTALK);
        String webhookUrl = config.getOrDefault("webhookUrl", defaultDingtalkWebhookUrl);
        String secret = config.getOrDefault("secret", defaultDingtalkSecret);
        String recipientsStr = config.getOrDefault("recipients", defaultRecipients);

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.error("钉钉 webhook URL 未配置，无法发送钉钉通知");
            return;
        }

        List<String> recipients = parseRecipients(recipientsStr);
        if (recipients.isEmpty()) {
            recipients = List.of(webhookUrl);
        }

        for (String recipient : recipients) {
            sendWithRetry(webhookUrl, secret, title, content, rule, recipient);
        }
    }

    @Async
    public void sendNotificationAsync(AlertRule rule, String title, String content) {
        sendDingtalkNotification(rule, title, content);
    }

    private void sendWithRetry(String webhookUrl, String secret, String title, String content,
                                AlertRule rule, String recipient) {
        int retryCount = 0;
        String lastError = null;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                boolean success = sendDingtalkMessage(webhookUrl, secret, title, content, rule);
                if (success) {
                    log.info("钉钉通知发送成功: title={}, recipient={}, attempt={}", title, recipient, retryCount + 1);
                    recordNotification(rule.getId(), NotificationChannel.DINGTALK, recipient, "SENT", null);
                    return;
                }
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("钉钉通知发送失败 (重试 {}/{}): title={}, error={}",
                        retryCount + 1, MAX_RETRY_COUNT, title, e.getMessage());
            }

            retryCount++;
            if (retryCount < MAX_RETRY_COUNT) {
                try {
                    Thread.sleep(RETRY_DELAY_MS * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.error("钉钉通知发送最终失败: title={}, recipient={}, error={}", title, recipient, lastError);
        recordNotification(rule.getId(), NotificationChannel.DINGTALK, recipient, "FAILED", lastError);
    }

    private boolean sendDingtalkMessage(String webhookUrl, String secret, String title,
                                        String content, AlertRule rule) {
        long timestamp = System.currentTimeMillis();
        String sign = generateSign(secret, timestamp);

        String urlWithParams = buildUrlWithParams(webhookUrl, timestamp, sign);
        Map<String, Object> body = buildMessageBody(title, content, rule);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            var response = restTemplate.postForEntity(urlWithParams, request, Map.class);
            if (response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errCode = (Integer) responseBody.get("errcode");
                String errmsg = (String) responseBody.get("errmsg");

                if (errCode != null && errCode == 0) {
                    log.debug("钉钉 API 响应成功: errcode={}, errmsg={}", errCode, errmsg);
                    return true;
                } else {
                    log.error("钉钉 API 响应错误: errcode={}, errmsg={}", errCode, errmsg);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("调用钉钉 API 异常: {}", e.getMessage());
            throw e;
        }

        return false;
    }

    private String buildUrlWithParams(String webhookUrl, long timestamp, String sign) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(webhookUrl)
                .queryParam("timestamp", timestamp)
                .queryParam("sign", sign);
        return builder.build().toUriString();
    }

    private Map<String, Object> buildMessageBody(String title, String content, AlertRule rule) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");

        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("title", title);
        markdown.put("text", buildMarkdownContent(title, content, rule));

        body.put("markdown", markdown);

        return body;
    }

    private String buildMarkdownContent(String title, String content, AlertRule rule) {
        StringBuilder sb = new StringBuilder();

        sb.append("### ").append(title).append("\n\n");
        sb.append("> **告警级别**: ").append(getAlertLevelText(rule.getAlertLevel())).append("\n\n");
        sb.append("---\n\n");
        sb.append("**告警内容**:\n\n");
        sb.append("```\n").append(content).append("\n```\n\n");
        sb.append("---\n\n");
        sb.append("**告警规则**: ").append(rule.getName() != null ? rule.getName() : "N/A").append("\n\n");
        sb.append("**触发时间**: ").append(LocalDateTime.now().toString().substring(0, 19)).append("\n");

        return sb.toString();
    }

    private String getAlertLevelText(AlertLevel level) {
        return switch (level) {
            case CRITICAL -> "🔴 严重 (P1)";
            case HIGH -> "🟠 高 (P2)";
            case MEDIUM -> "🟡 中 (P3)";
            case LOW -> "🟢 低 (P4)";
            case INFO -> "🔵 信息";
        };
    }

    private String generateSign(String secret, long timestamp) {
        if (secret == null || secret.isEmpty()) {
            return "";
        }

        try {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return URLEncoder.encode(new String(Base64.encodeBase64(signBytes)), "UTF-8");
        } catch (Exception e) {
            log.error("生成钉钉签名失败: {}", e.getMessage());
            return "";
        }
    }

    private Map<String, String> getChannelConfig(NotificationChannel channel) {
        Map<String, String> config = new HashMap<>();

        try {
            NotificationChannelConfig dbConfig = channelConfigRepository
                    .findByChannel(channel)
                    .orElse(null);

            if (dbConfig != null && dbConfig.getEnabled() && dbConfig.getConfigParams() != null) {
                Map<String, String> params = objectMapper.readValue(
                        dbConfig.getConfigParams(),
                        Map.class
                );
                config.putAll(params);
                config.put("_enabled", "true");
                log.info("钉钉渠道配置已从数据库加载: enabled={}", dbConfig.getEnabled());
            } else {
                log.info("钉钉渠道未启用或无数据库配置，使用默认配置");
            }
        } catch (Exception e) {
            log.warn("读取钉钉渠道配置失败: {}", channel, e);
        }

        return config;
    }

    private List<String> parseRecipients(String recipientsStr) {
        if (recipientsStr == null || recipientsStr.isEmpty()) {
            return new ArrayList<>();
        }

        return Arrays.stream(recipientsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private void recordNotification(UUID alertRecordId, NotificationChannel channel,
                                     String recipient, String status, String errorMessage) {
        try {
            AlertNotification notification = AlertNotification.builder()
                    .alertRecordId(alertRecordId)
                    .channel(channel)
                    .recipient(recipient)
                    .status(status)
                    .errorMessage(errorMessage)
                    .sentAt("SENT".equals(status) ? LocalDateTime.now() : null)
                    .build();

            notificationRepository.save(notification);
            log.debug("钉钉通知记录已保存: alertRecordId={}, status={}", alertRecordId, status);
        } catch (Exception e) {
            log.error("保存钉钉通知记录失败: alertRecordId={}, error={}", alertRecordId, e.getMessage());
        }
    }

    public boolean testDingtalkConnection(String webhookUrl, String secret) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.error("测试连接失败: webhook URL 为空");
            return false;
        }

        try {
            long timestamp = System.currentTimeMillis();
            String sign = generateSign(secret, timestamp);

            String urlWithParams = buildUrlWithParams(webhookUrl, timestamp, sign);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msgtype", "markdown");

            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("title", "🔔 钉钉告警连接测试");
            markdown.put("text", buildTestMarkdownContent());

            body.put("markdown", markdown);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            var response = restTemplate.postForEntity(urlWithParams, request, Map.class);

            if (response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errCode = (Integer) responseBody.get("errcode");
                String errmsg = (String) responseBody.get("errmsg");

                if (errCode != null && errCode == 0) {
                    log.info("钉钉连接测试成功");
                    return true;
                } else {
                    log.error("钉钉连接测试失败: errcode={}, errmsg={}", errCode, errmsg);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("钉钉连接测试异常: {}", e.getMessage());
            return false;
        }

        return false;
    }

    private String buildTestMarkdownContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("### 🔔 钉钉告警连接测试\n\n");
        sb.append("**状态**: ✅ 连接成功\n\n");
        sb.append("**时间**: ").append(LocalDateTime.now().toString().substring(0, 19)).append("\n\n");
        sb.append("---\n\n");
        sb.append("**说明**: 您的钉钉告警配置已成功连接到此日志分析系统。\n\n");
        sb.append("---\n\n");
        sb.append("🎉 **配置正确！** 您现在可以正常接收钉钉告警通知。");
        return sb.toString();
    }
}
