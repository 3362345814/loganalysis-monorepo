package com.evelin.loganalysis.logalert.repository;

import com.evelin.loganalysis.logalert.enums.NotificationChannel;
import com.evelin.loganalysis.logalert.model.AlertNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 告警通知Repository
 *
 * @author Evelin
 */
@Repository
public interface AlertNotificationRepository extends JpaRepository<AlertNotification, UUID> {

    /**
     * 根据告警记录ID查询通知列表
     */
    List<AlertNotification> findByAlertRecordId(UUID alertRecordId);

    /**
     * 根据通知渠道查询
     */
    List<AlertNotification> findByChannel(NotificationChannel channel);

    /**
     * 查询待发送的通知
     */
    List<AlertNotification> findByStatus(String status);

    /**
     * 查询发送失败的通知（可重试）
     */
    List<AlertNotification> findByStatusAndRetryCountLessThan(String status, int maxRetries);
}
