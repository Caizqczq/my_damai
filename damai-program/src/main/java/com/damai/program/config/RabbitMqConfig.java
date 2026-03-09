package com.damai.program.config;

import com.damai.common.constant.MqConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.amqp.core.BindingBuilder.bind;

@Slf4j
@Configuration
public class RabbitMqConfig {

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter mc) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(mc);
        template.setMandatory(true);
        template.setReturnsCallback(returned ->
                log.error("MQ消息路由失败: exchange={}, routingKey={}, replyText={}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return template;
    }

    @Bean
    public Declarables mqTopology() {
        DirectExchange ex = new DirectExchange(MqConstant.EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(MqConstant.DLX, true, false);

        Queue orderQ = QueueBuilder.durable(MqConstant.ORDER_CREATE)
                .deadLetterExchange(MqConstant.DLX)
                .deadLetterRoutingKey(MqConstant.ORDER_CREATE_DLQ)
                .build();
        Queue orderDlq = QueueBuilder.durable(MqConstant.ORDER_CREATE_DLQ).build();

        Queue stockRestoreQ = QueueBuilder.durable(MqConstant.STOCK_RESTORE)
                .deadLetterExchange(MqConstant.DLX)
                .deadLetterRoutingKey(MqConstant.STOCK_RESTORE_DLQ)
                .build();
        Queue stockRestoreDlq = QueueBuilder.durable(MqConstant.STOCK_RESTORE_DLQ).build();

        return new Declarables(
                ex, dlx,
                orderQ, orderDlq,
                stockRestoreQ, stockRestoreDlq,
                bind(orderQ).to(ex).with(MqConstant.ORDER_CREATE),
                bind(orderDlq).to(dlx).with(MqConstant.ORDER_CREATE_DLQ),
                bind(stockRestoreQ).to(ex).with(MqConstant.STOCK_RESTORE),
                bind(stockRestoreDlq).to(dlx).with(MqConstant.STOCK_RESTORE_DLQ)
        );
    }
}
