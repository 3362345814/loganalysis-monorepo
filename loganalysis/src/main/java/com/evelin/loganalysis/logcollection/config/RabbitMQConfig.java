package com.evelin.loganalysis.logcollection.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 * 用于日志采集后的脱敏处理
 *
 * @author Evelin
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 交换机名称
     */
    public static final String LOG_EXCHANGE = "log.exchange";

    /**
     * 日志队列名称（脱敏处理）
     */
    public static final String LOG_DESENSITIZE_QUEUE = "log.desensitize.queue";

    /**
     * 路由键
     */
    public static final String LOG_ROUTING_KEY = "log.raw.#";

    /**
     * 死信交换机
     */
    public static final String LOG_DLX_EXCHANGE = "log.dlx.exchange";

    /**
     * 死信队列
     */
    public static final String LOG_DLQ_QUEUE = "log.dlq.queue";

    /**
     * 创建交换机（使用 TopicExchange 支持通配符）
     */
    @Bean
    public TopicExchange logExchange() {
        return new TopicExchange(LOG_EXCHANGE, true, false);
    }

    /**
     * 创建死信交换机
     */
    @Bean
    public TopicExchange logDlxExchange() {
        return new TopicExchange(LOG_DLX_EXCHANGE, true, false);
    }

    /**
     * 创建日志队列（用于脱敏处理）
     */
    @Bean
    public Queue logDesensitizeQueue() {
        return QueueBuilder.durable(LOG_DESENSITIZE_QUEUE)
                .withArgument("x-dead-letter-exchange", LOG_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "log.dlq")
                .build();
    }

    /**
     * 创建死信队列
     */
    @Bean
    public Queue logDlqQueue() {
        return QueueBuilder.durable(LOG_DLQ_QUEUE).build();
    }

    /**
     * 绑定日志队列到交换机
     */
    @Bean
    public Binding logDesensitizeBinding() {
        return BindingBuilder
                .bind(logDesensitizeQueue())
                .to(logExchange())
                .with(LOG_ROUTING_KEY);
    }

    /**
     * 绑定死信队列
     */
    @Bean
    public Binding logDlqBinding() {
        return BindingBuilder
                .bind(logDlqQueue())
                .to(logDlxExchange())
                .with("log.dlq");
    }

    /**
     * JSON 消息转换器
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate 配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
