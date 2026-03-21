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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuNotificationService {

    private final AlertNotificationRepository notificationRepository;
    private final NotificationChannelConfigRepository channelConfigRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String FEISHU_SIGN_CACHE_KEY = "feishu:sign:";
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 1000;

    @Value("${alert.notification.feishu.webhook-url:}")
    private String defaultFeishuWebhookUrl;

    @Value("${alert.notification.feishu.secret:}")
    private String defaultFeishuSecret;

    @Value("${alert.notification.feishu.default-recipients:}")
    private String defaultRecipients;

    public void sendFeishuNotification(AlertRule rule, String title, String content) {
        Map<String, String> config = getChannelConfig(NotificationChannel.FEISHU);
        String webhookUrl = config.getOrDefault("webhookUrl", defaultFeishuWebhookUrl);
        String secret = config.getOrDefault("secret", defaultFeishuSecret);
        String recipientsStr = config.getOrDefault("recipients", defaultRecipients);

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.error("飞书 webhook URL 未配置，无法发送飞书通知");
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
        sendFeishuNotification(rule, title, content);
    }

    private void sendWithRetry(String webhookUrl, String secret, String title, String content,
                                AlertRule rule, String recipient) {
        int retryCount = 0;
        String lastError = null;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                boolean success = sendFeishuMessage(webhookUrl, secret, title, content, rule);
                if (success) {
                    log.info("飞书通知发送成功: title={}, recipient={}, attempt={}", title, recipient, retryCount + 1);
                    recordNotification(rule.getId(), NotificationChannel.FEISHU, recipient, "SENT", null);
                    return;
                }
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("飞书通知发送失败 (重试 {}/{}): title={}, error={}",
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

        log.error("飞书通知发送最终失败: title={}, recipient={}, error={}", title, recipient, lastError);
        recordNotification(rule.getId(), NotificationChannel.FEISHU, recipient, "FAILED", lastError);
    }

    private boolean sendFeishuMessage(String webhookUrl, String secret, String title,
                                      String content, AlertRule rule) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String sign = generateSign(secret, timestamp);

        Map<String, Object> body = buildMessageBody(title, content, rule, timestamp, sign);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            var response = restTemplate.postForEntity(webhookUrl, request, Map.class);
            if (response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer code = (Integer) responseBody.get("code");
                String msg = (String) responseBody.get("msg");

                if (code != null && code == 0) {
                    log.debug("飞书 API 响应成功: code={}, msg={}", code, msg);
                    return true;
                } else {
                    log.error("飞书 API 响应错误: code={}, msg={}", code, msg);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("调用飞书 API 异常: {}", e.getMessage());
            throw e;
        }

        return false;
    }

    private Map<String, Object> buildMessageBody(String title, String content, AlertRule rule,
                                                   String timestamp, String sign) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "interactive");
        body.put("card", buildAlertCard(title, content, rule));
        return body;
    }

    private Map<String, Object> buildAlertCard(String title, String content, AlertRule rule) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", "2.0");

        card.put("config", buildCardConfig());
        card.put("header", buildCardHeader(title, rule.getAlertLevel()));
        card.put("body", buildCardBody(title, content, rule));

        return card;
    }

    private Map<String, Object> buildCardConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("wide_screen_mode", true);
        return config;
    }

    private Map<String, Object> buildCardHeader(String title, AlertLevel alertLevel) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("title", buildHeaderTitle(title));
        header.put("template", getAlertLevelColor(alertLevel));
        header.put("padding", "12px 12px 12px 12px");
        return header;
    }

    private Map<String, Object> buildHeaderTitle(String content) {
        Map<String, Object> title = new LinkedHashMap<>();
        title.put("tag", "plain_text");
        title.put("content", content);
        return title;
    }

    private Map<String, Object> buildCardBody(String title, String content, AlertRule rule) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("direction", "vertical");
        body.put("padding", "12px 12px 12px 12px");

        List<Map<String, Object>> elements = new ArrayList<>();

        elements.add(buildMarkdown("**告警级别**: " + getAlertLevelText(rule.getAlertLevel())));
        elements.add(buildHr());
        elements.add(buildMarkdown("**告警内容**:"));
        elements.add(buildMarkdown(content));
        elements.add(buildHr());
        elements.add(buildMarkdown("**告警规则**: " + (rule.getName() != null ? rule.getName() : "N/A")));
        elements.add(buildMarkdown("**触发时间**: " + LocalDateTime.now().toString().substring(0, 19)));

        body.put("elements", elements);
        return body;
    }

    private Map<String, Object> buildMarkdown(String content) {
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("tag", "markdown");
        element.put("content", content);
        return element;
    }

    private Map<String, Object> buildHr() {
        Map<String, Object> hr = new LinkedHashMap<>();
        hr.put("tag", "hr");
        return hr;
    }

    private String getAlertLevelColor(AlertLevel level) {
        return switch (level) {
            case CRITICAL -> "red";
            case HIGH -> "orange";
            case MEDIUM -> "yellow";
            case LOW -> "green";
            case INFO -> "blue";
        };
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

    private String generateSign(String secret, String timestamp) {
        if (secret == null || secret.isEmpty()) {
            return "";
        }

        try {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] signBytes = mac.doFinal(new byte[]{});
            return Base64.getEncoder().encodeToString(signBytes);
        } catch (Exception e) {
            log.error("生成飞书签名失败: {}", e.getMessage());
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
                log.info("飞书渠道配置已从数据库加载: enabled={}", dbConfig.getEnabled());
            } else {
                log.info("飞书渠道未启用或无数据库配置，使用默认配置");
            }
        } catch (Exception e) {
            log.warn("读取飞书渠道配置失败: {}", channel, e);
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
            log.debug("飞书通知记录已保存: alertRecordId={}, status={}", alertRecordId, status);
        } catch (Exception e) {
            log.error("保存飞书通知记录失败: alertRecordId={}, error={}", alertRecordId, e.getMessage());
        }
    }

    public boolean testFeishuConnection(String webhookUrl, String secret) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.error("测试连接失败: webhook URL 为空");
            return false;
        }

        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String sign = generateSign(secret, timestamp);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msg_type", "interactive");
            body.put("card", buildTestCard());

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            var response = restTemplate.postForEntity(webhookUrl, request, Map.class);

            if (response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer code = (Integer) responseBody.get("code");
                String msg = (String) responseBody.get("msg");

                if (code != null && code == 0) {
                    log.info("飞书连接测试成功");
                    return true;
                } else {
                    log.error("飞书连接测试失败: code={}, msg={}", code, msg);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("飞书连接测试异常: {}", e.getMessage());
            return false;
        }

        return false;
    }

    private Map<String, Object> buildTestCard() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", "2.0");
        card.put("config", buildCardConfig());

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("title", buildHeaderTitle("🔔 飞书告警连接测试"));
        header.put("template", "blue");
        header.put("padding", "12px 12px 12px 12px");
        card.put("header", header);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("direction", "vertical");
        body.put("padding", "12px 12px 12px 12px");

        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(buildMarkdown("**状态**: ✅ 连接成功"));
        elements.add(buildMarkdown("**时间**: " + LocalDateTime.now().toString().substring(0, 19)));
        elements.add(buildMarkdown("**说明**: 您的飞书告警配置已成功连接到此日志分析系统。"));
        elements.add(buildHr());
        elements.add(buildMarkdown("🎉 **配置正确！** 您现在可以正常接收飞书告警通知。"));

        body.put("elements", elements);
        card.put("body", body);

        return card;
    }
}
