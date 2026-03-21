package com.evelin.loganalysis.logalert.repository;

import com.evelin.loganalysis.logalert.enums.NotificationChannel;
import com.evelin.loganalysis.logalert.model.NotificationChannelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 通知渠道配置Repository
 *
 * @author Evelin
 */
@Repository
public interface NotificationChannelConfigRepository extends JpaRepository<NotificationChannelConfig, Long> {

    /**
     * 根据渠道查询配置
     */
    Optional<NotificationChannelConfig> findByChannel(NotificationChannel channel);

    /**
     * 查询已启用的渠道配置
     */
    List<NotificationChannelConfig> findByEnabledTrue();

    /**
     * 查询所有渠道配置
     */
    List<NotificationChannelConfig> findAllByOrderByChannelAsc();
}
