package com.damai.program.config;

import com.damai.common.constant.MqConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
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
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(mc);
        // 开启 mandatory：消息无法路由到队列时回调
        t.setMandatory(true);
        t.setReturnsCallback(returned ->
                log.error("MQ消息路由失败: exchange={}, routingKey={}, replyText={}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return t;
    }

    @Bean
    public Declarables mqTopology() {
        DirectExchange ex  = new DirectExchange(MqConstant.EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(MqConstant.DLX, true, false);

        Queue orderQ    = QueueBuilder.durable(MqConstant.ORDER_CREATE).deadLetterExchange(MqConstant.DLX).deadLetterRoutingKey(MqConstant.ORDER_CREATE_DLQ).build();
        Queue orderDlq  = QueueBuilder.durable(MqConstant.ORDER_CREATE_DLQ).build();
        Queue seatOpsQ  = QueueBuilder.durable(MqConstant.SEAT_OPS).deadLetterExchange(MqConstant.DLX).deadLetterRoutingKey(MqConstant.SEAT_OPS_DLQ).build();
        Queue seatDlq   = QueueBuilder.durable(MqConstant.SEAT_OPS_DLQ).build();

        Queue delayQ = QueueBuilder.durable(MqConstant.ORDER_DELAY)
                .ttl(MqConstant.ORDER_EXPIRE_MINUTES * 60 * 1000)
                .deadLetterExchange(MqConstant.DLX)
                .deadLetterRoutingKey(MqConstant.ORDER_DELAY_CHECK)
                .build();
        Queue checkQ = QueueBuilder.durable(MqConstant.ORDER_DELAY_CHECK).build();

        return new Declarables(
                ex, dlx,
                orderQ, orderDlq, seatOpsQ, seatDlq,
                delayQ, checkQ,
                bind(orderQ).to(ex).with(MqConstant.ORDER_CREATE),
                bind(orderDlq).to(dlx).with(MqConstant.ORDER_CREATE_DLQ),
                bind(seatOpsQ).to(ex).with(MqConstant.SEAT_OPS),
                bind(seatDlq).to(dlx).with(MqConstant.SEAT_OPS_DLQ),
                bind(delayQ).to(ex).with(MqConstant.ORDER_DELAY),
                bind(checkQ).to(dlx).with(MqConstant.ORDER_DELAY_CHECK)
        );
    }
}
