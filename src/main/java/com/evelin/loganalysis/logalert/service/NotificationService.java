package com.evelin.loganalysis.logalert.service;

import com.evelin.loganalysis.logalert.model.NotificationChannelConfig;
import com.evelin.loganalysis.logalert.repository.NotificationChannelConfigRepository;
import com.evelin.loganalysis.logalert.enums.NotificationChannel;
import com.evelin.loganalysis.logalert.model.AlertNotification;
import com.evelin.loganalysis.logalert.model.AlertRule;
import com.evelin.loganalysis.logalert.repository.AlertNotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * 通知服务
 *
 * @author Evelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AlertNotificationRepository notificationRepository;
    private final NotificationChannelConfigRepository channelConfigRepository;
    private final ObjectMapper objectMapper;
    private final FeishuNotificationService feishuNotificationService;

    // 默认配置（当数据库没有配置时使用）
    @Value("${alert.notification.dingtalk.webhook-url:}")
    private String defaultDingtalkWebhookUrl;

    @Value("${alert.notification.dingtalk.secret:}")
    private String defaultDingtalkSecret;

    @Value("${alert.notification.weixin.webhook-url:}")
    private String defaultWeixinWebhookUrl;

    @Value("${alert.notification.feishu.webhook-url:}")
    private String defaultFeishuWebhookUrl;

    @Value("${alert.notification.feishu.secret:}")
    private String defaultFeishuSecret;

    @Value("${alert.notification.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${alert.notification.email.smtp-host:}")
    private String defaultSmtpHost;

    @Value("${alert.notification.email.smtp-port:587}")
    private int defaultSmtpPort;

    @Value("${alert.notification.email.from:}")
    private String defaultEmailFrom;

    @Value("${alert.notification.email.username:}")
    private String defaultEmailUsername;

    @Value("${alert.notification.email.password:}")
    private String defaultEmailPassword;

    /**
     * 获取渠道配置（优先从数据库读取，否则使用默认配置）
     */
    private Map<String, String> getChannelConfig(NotificationChannel channel) {
        Map<String, String> config = new HashMap<>();
        
        try {
            NotificationChannelConfig dbConfig = channelConfigRepository
                    .findByChannel(channel)
                    .orElse(null);
            
            log.info("渠道 {} 的数据库配置: {}", channel, dbConfig);
            
            if (dbConfig != null && dbConfig.getEnabled() && dbConfig.getConfigParams() != null) {
                Map<String, String> params = objectMapper.readValue(
                        dbConfig.getConfigParams(), 
                        Map.class
                );
                log.info("渠道 {} 的配置参数: {}", channel, params);
                config.putAll(params);
                config.put("_enabled", "true");
                return config;
            } else {
                log.info("渠道 {} 未启用或无配置，使用默认配置", channel);
            }
        } catch (Exception e) {
            log.warn("读取渠道配置失败: {}", channel, e);
        }
        
        // 使用默认配置
        config.put("_enabled", "false");
        return config;
    }

    /**
     * 发送通知
     */
    public void sendNotifications(AlertRule rule, String title, String content) {
        List<String> channels = rule.getNotificationChannels();
        if (channels == null || channels.isEmpty()) {
            log.warn("规则 {} 没有配置通知渠道", rule.getName());
            return;
        }

        for (String channel : channels) {
            try {
                NotificationChannel notificationChannel = NotificationChannel.valueOf(channel.toUpperCase());
                sendNotification(notificationChannel, title, content, rule);
            } catch (IllegalArgumentException e) {
                log.error("无效的通知渠道: {}", channel);
            }
        }
    }

    /**
     * 发送单个通知
     */
    @Async
    public void sendNotification(NotificationChannel channel, String title, String content, AlertRule rule) {
        try {
            switch (channel) {
                case DINGTALK -> sendDingtalkNotification(title, content);
                case WECHAT -> sendWeixinNotification(title, content);
                case FEISHU -> sendFeishuNotification(title, content, rule);
                case EMAIL -> sendEmailNotification(title, content, rule);
                case WEBHOOK -> sendWebhookNotification(title, content);
                default -> log.warn("不支持的通知渠道: {}", channel);
            }
        } catch (Exception e) {
            log.error("发送通知失败: channel={}", channel, e);
        }
    }

    /**
     * 发送钉钉通知
     */
    private void sendDingtalkNotification(String title, String content) {
        Map<String, String> config = getChannelConfig(NotificationChannel.DINGTALK);
        String webhookUrl = config.getOrDefault("webhookUrl", defaultDingtalkWebhookUrl);
        String secret = config.getOrDefault("secret", defaultDingtalkSecret);
        
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("钉钉 webhook URL 未配置");
            return;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "text");

            Map<String, Object> text = new HashMap<>();
            text.put("content", String.format("【%s】\n%s", title, content));
            body.put("text", text);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(webhookUrl, request, String.class);
            log.info("钉钉通知发送成功");
        } catch (Exception e) {
            log.error("发送钉钉通知失败", e);
        }
    }

    /**
     * 发送企业微信通知
     */
    private void sendWeixinNotification(String title, String content) {
        Map<String, String> config = getChannelConfig(NotificationChannel.WECHAT);
        String webhookUrl = config.getOrDefault("webhookUrl", defaultWeixinWebhookUrl);
        
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("企业微信 webhook URL 未配置");
            return;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "text");

            Map<String, Object> text = new HashMap<>();
            text.put("content", String.format("【%s】\n%s", title, content));
            body.put("text", text);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(webhookUrl, request, String.class);
            log.info("企业微信通知发送成功");
        } catch (Exception e) {
            log.error("发送企业微信通知失败", e);
        }
    }

    /**
     * 发送飞书通知
     */
    private void sendFeishuNotification(String title, String content, AlertRule rule) {
        try {
            feishuNotificationService.sendFeishuNotification(rule, title, content);
            log.info("飞书通知发送请求已提交");
        } catch (Exception e) {
            log.error("发送飞书通知失败: channel={}, error={}", NotificationChannel.FEISHU, e.getMessage());
        }
    }

    /**
     * 发送邮件通知
     */
    private void sendEmailNotification(String title, String content, AlertRule rule) {
        Map<String, String> config = getChannelConfig(NotificationChannel.EMAIL);
        boolean isEnabled = "true".equals(config.get("_enabled")) || emailEnabled;

        if (!isEnabled) {
            log.warn("邮件通知未启用");
            return;
        }

        String smtpHost = config.getOrDefault("smtpHost", defaultSmtpHost);
        String smtpPort = config.getOrDefault("smtpPort", String.valueOf(defaultSmtpPort));
        String username = config.getOrDefault("username", defaultEmailUsername);
        String password = config.getOrDefault("password", defaultEmailPassword);
        String from = config.getOrDefault("from", defaultEmailFrom);
        String recipientsStr = config.get("recipients");

        log.info("邮件配置 - smtpHost: {}, smtpPort: {}, username: {}, from: {}, recipients: {}", 
                smtpHost, smtpPort, username, from, recipientsStr);

        if (smtpHost == null || smtpHost.isEmpty()) {
            log.warn("邮件 SMTP 服务器未配置");
            return;
        }

        if (recipientsStr == null || recipientsStr.isEmpty()) {
            log.warn("邮件收件人未配置");
            return;
        }

        // 解析收件人列表（支持逗号分隔的多个收件人）
        List<String> recipients = Arrays.asList(recipientsStr.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (recipients.isEmpty()) {
            log.warn("邮件收件人列表为空");
            return;
        }

        try {
            // 构建邮件内容
            String emailContent = buildEmailContent(title, content, rule);

            // 使用数据库配置创建 JavaMailSender
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(smtpHost);
            mailSender.setPort(Integer.parseInt(smtpPort));
            mailSender.setUsername(username);
            mailSender.setPassword(password);
            mailSender.setDefaultEncoding("UTF-8");
            
            // 配置 SMTP 属性
            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            if (Integer.parseInt(smtpPort) == 465) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.trust", smtpHost);
            } else {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }

            log.info("创建邮件发送器 - host: {}, port: {}, username: {}", smtpHost, smtpPort, username);

            // 发送邮件
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            // 设置发件人
            if (from != null && !from.isEmpty()) {
                helper.setFrom(from);
            } else {
                helper.setFrom(username);
            }

            // 设置收件人
            helper.setTo(recipients.toArray(new String[0]));

            // 设置主题
            helper.setSubject(title);

            // 设置邮件内容（支持HTML）
            helper.setText(emailContent, true);

            // 发送邮件
            mailSender.send(mimeMessage);

            log.info("邮件通知发送成功: {} -> {}", from, recipients);

            // 记录通知发送结果
            for (String recipient : recipients) {
                recordNotification(rule.getId(), NotificationChannel.EMAIL, recipient, "SENT", null);
            }
        } catch (MessagingException e) {
            log.error("发送邮件通知失败: {}", e.getMessage(), e);
            recordNotification(rule.getId(), NotificationChannel.EMAIL, recipientsStr, "FAILED", e.getMessage());
        } catch (Exception e) {
            log.error("发送邮件通知失败: {}", e.getMessage(), e);
            recordNotification(rule.getId(), NotificationChannel.EMAIL, recipientsStr, "FAILED", e.getMessage());
        }
    }

    /**
     * 构建邮件内容
     */
    private String buildEmailContent(String title, String content, AlertRule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<div style='font-family: Arial, sans-serif; padding: 20px; max-width: 800px;'>");
        sb.append("<h2 style='color: #d9534f;'>").append(title).append("</h2>");
        sb.append("<div style='background-color: #f8f9fa; padding: 15px; border-radius: 5px; margin: 15px 0;'>");
        sb.append("<p><strong>告警内容:</strong></p>");
        sb.append("<p>").append(content.replace("\n", "<br>")).append("</p>");
        sb.append("</div>");
        sb.append("<div style='color: #6c757d; font-size: 12px; margin-top: 20px;'>");
        sb.append("<p><strong>告警规则:</strong> ").append(rule.getName()).append("</p>");
        sb.append("<p><strong>告警级别:</strong> ").append(rule.getAlertLevel()).append("</p>");
        sb.append("<p><strong>触发时间:</strong> ").append(LocalDateTime.now()).append("</p>");
        sb.append("</div>");
        sb.append("</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * 发送通用 Webhook 通知
     */
    private void sendWebhookNotification(String title, String content) {
        Map<String, String> config = getChannelConfig(NotificationChannel.WEBHOOK);
        String webhookUrl = config.get("webhookUrl");
        
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("Webhook URL 未配置");
            return;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("title", title);
            body.put("content", content);
            body.put("timestamp", System.currentTimeMillis());

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(webhookUrl, request, String.class);
            log.info("Webhook 通知发送成功");
        } catch (Exception e) {
            log.error("发送 Webhook 通知失败", e);
        }
    }

    /**
     * 记录通知发送结果
     */
    public void recordNotification(UUID alertRecordId, NotificationChannel channel,
                                   String recipient, String status, String errorMessage) {
        AlertNotification notification = AlertNotification.builder()
                .alertRecordId(alertRecordId)
                .channel(channel)
                .recipient(recipient)
                .status(status)
                .errorMessage(errorMessage)
                .sentAt("SENT".equals(status) ? LocalDateTime.now() : null)
                .build();

        notificationRepository.save(notification);
    }
}
