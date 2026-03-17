package com.evelin.loganalysis.logalert.model;

import com.evelin.loganalysis.logalert.enums.NotificationChannel;
import com.evelin.loganalysis.logcommon.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 告警通知记录实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "alert_notifications", indexes = {
        @Index(name = "idx_alert_notification_record_id", columnList = "alert_record_id"),
        @Index(name = "idx_alert_notification_channel", columnList = "channel"),
        @Index(name = "idx_alert_notification_status", columnList = "status")
})
public class AlertNotification extends BaseEntity {

    /**
     * 关联的告警记录ID
     */
    @Column(name = "alert_record_id", nullable = false)
    private UUID alertRecordId;

    /**
     * 通知渠道
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 50)
    private NotificationChannel channel;

    /**
     * 接收者（邮箱/手机号/Webhook URL等）
     */
    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;

    /**
     * Webhook URL（用于Webhook类型通知）
     */
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    /**
     * 通知内容
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 通知状态：PENDING, SENT, FAILED
     */
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "PENDING";

    /**
     * 发送时间
     */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 重试次数
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
}
