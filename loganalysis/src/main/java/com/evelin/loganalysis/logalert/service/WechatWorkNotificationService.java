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

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatWorkNotificationService {

    private final AlertNotificationRepository notificationRepository;
    private final NotificationChannelConfigRepository channelConfigRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 1000;

    @Value("${alert.notification.wechatwork.webhook-url:}")
    private String defaultWechatWorkWebhookUrl;

    @Value("${alert.notification.wechatwork.default-recipients:}")
    private String defaultRecipients;

    public void sendWechatWorkNotification(AlertRule rule, String title, String content) {
        Map<String, String> config = getChannelConfig(NotificationChannel.WECHAT);
        String webhookUrl = config.getOrDefault("webhookUrl", defaultWechatWorkWebhookUrl);
        String recipientsStr = config.getOrDefault("recipients", defaultRecipients);

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.error("企业微信 webhook URL 未配置，无法发送通知");
            return;
        }

        List<String> recipients = parseRecipients(recipientsStr);
        if (recipients.isEmpty()) {
            recipients = List.of(webhookUrl);
        }

        for (String recipient : recipients) {
            sendWithRetry(webhookUrl, title, content, rule, recipient);
        }
    }

    @Async
    public void sendNotificationAsync(AlertRule rule, String title, String content) {
        sendWechatWorkNotification(rule, title, content);
    }

    private void sendWithRetry(String webhookUrl, String title, String content,
                                AlertRule rule, String recipient) {
        int retryCount = 0;
        String lastError = null;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                boolean success = sendWechatWorkMessage(webhookUrl, title, content, rule);
                if (success) {
                    log.info("企业微信通知发送成功: title={}, recipient={}, attempt={}", title, recipient, retryCount + 1);
                    recordNotification(rule.getId(), NotificationChannel.WECHAT, recipient, "SENT", null);
                    return;
                }
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("企业微信通知发送失败 (重试 {}/{}): title={}, error={}",
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

        log.error("企业微信通知发送最终失败: title={}, recipient={}, error={}", title, recipient, lastError);
        recordNotification(rule.getId(), NotificationChannel.WECHAT, recipient, "FAILED", lastError);
    }

    private boolean sendWechatWorkMessage(String webhookUrl, String title,
                                          String content, AlertRule rule) {
        Map<String, Object> body = buildMessageBody(title, content, rule);

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            var response = restTemplate.postForEntity(webhookUrl, request, Map.class);
            if (response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errCode = (Integer) responseBody.get("errcode");
                String errmsg = (String) responseBody.get("errmsg");

                if (errCode != null && errCode == 0) {
                    log.debug("企业微信 API 响应成功: errcode={}, errmsg={}", errCode, errmsg);
                    return true;
                } else {
                    log.error("企业微信 API 响应错误: errcode={}, errmsg={}", errCode, errmsg);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("调用企业微信 API 异常: {}", e.getMessage());
            throw e;
        }

        return false;
    }

    private Map<String, Object> buildMessageBody(String title, String content, AlertRule rule) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");

        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("content", buildMarkdownContent(title, content, rule));

        body.put("markdown", markdown);

        return body;
    }

    private String buildMarkdownContent(String title, String content, AlertRule rule) {
        StringBuilder sb = new StringBuilder();

        sb.append("### ").append(title).append("\n\n");
        sb.append(">**告警级别**: ").append(getAlertLevelText(rule.getAlertLevel())).append("\n\n");
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
                log.info("企业微信渠道配置已从数据库加载: enabled={}", dbConfig.getEnabled());
            } else {
                log.info("企业微信渠道未启用或无数据库配置，使用默认配置");
            }
        } catch (Exception e) {
            log.warn("读取企业微信渠道配置失败: {}", channel, e);
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
            log.debug("企业微信通知记录已保存: alertRecordId={}, status={}", alertRecordId, status);
        } catch (Exception e) {
            log.error("保存企业微信通知记录失败: alertRecordId={}, error={}", alertRecordId, e.getMessage());
        }
    }

    public boolean testWechatWorkConnection(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.error("测试连接失败: webhook URL 为空");
            return false;
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msgtype", "markdown");

            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("content", buildTestMarkdownContent());

            body.put("markdown", markdown);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            var response = restTemplate.postForEntity(webhookUrl, request, Map.class);

            if (response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errCode = (Integer) responseBody.get("errcode");
                String errmsg = (String) responseBody.get("errmsg");

                if (errCode != null && errCode == 0) {
                    log.info("企业微信连接测试成功");
                    return true;
                } else {
                    log.error("企业微信连接测试失败: errcode={}, errmsg={}", errCode, errmsg);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("企业微信连接测试异常: {}", e.getMessage());
            return false;
        }

        return false;
    }

    private String buildTestMarkdownContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("### 🔔 企业微信告警连接测试\n\n");
        sb.append("**状态**: ✅ 连接成功\n\n");
        sb.append("**时间**: ").append(LocalDateTime.now().toString().substring(0, 19)).append("\n\n");
        sb.append("---\n\n");
        sb.append("**说明**: 您的企业微信告警配置已成功连接到此日志分析系统。\n\n");
        sb.append("---\n\n");
        sb.append("🎉 **配置正确！** 您现在可以正常接收企业微信告警通知。");
        return sb.toString();
    }
}
