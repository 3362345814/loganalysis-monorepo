package com.evelin.loganalysis.logcommon.model;

import com.evelin.loganalysis.logcommon.enums.NotificationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 告警通知实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "alert_notification", indexes = {
        @Index(name = "idx_alert_notification_record_id", columnList = "record_id"),
        @Index(name = "idx_alert_notification_channel", columnList = "channel")
})
public class AlertNotification extends BaseEntity {

    /**
     * 告警记录ID
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private AlertRecord recordId;

    /**
     * 通知渠道
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 50)
    private NotificationChannel channel;

    /**
     * 接收人
     */
    @Column(name = "recipient", length = 255)
    private String recipient;

    /**
     * 通知内容
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 发送状态
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /**
     * 发送时间
     */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /**
     * 发送失败原因
     */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /**
     * 重试次数
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
}
