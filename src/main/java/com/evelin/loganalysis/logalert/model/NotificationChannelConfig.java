package com.evelin.loganalysis.logalert.model;

import com.evelin.loganalysis.logalert.enums.NotificationChannel;
import com.evelin.loganalysis.logcommon.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 通知渠道配置实体
 *
 * @author Evelin
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "notification_channel_configs", indexes = {
        @Index(name = "idx_channel_config_channel", columnList = "channel", unique = true)
})
public class NotificationChannelConfig extends BaseEntity {

    /**
     * 通知渠道
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, unique = true, length = 20)
    private NotificationChannel channel;

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    /**
     * 配置参数 (JSON格式)
     */
    @Column(name = "config_params", columnDefinition = "TEXT")
    private String configParams;

    /**
     * 描述
     */
    @Column(name = "description", length = 500)
    private String description;
}
