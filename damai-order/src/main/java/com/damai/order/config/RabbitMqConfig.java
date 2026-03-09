package com.damai.order.config;

import com.damai.common.constant.MqConstant;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.amqp.core.BindingBuilder.bind;

@Configuration
public class RabbitMqConfig {

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
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

        Queue seatAllocateQ = QueueBuilder.durable(MqConstant.SEAT_ALLOCATE)
                .deadLetterExchange(MqConstant.DLX)
                .deadLetterRoutingKey(MqConstant.SEAT_ALLOCATE_DLQ)
                .build();
        Queue seatAllocateDlq = QueueBuilder.durable(MqConstant.SEAT_ALLOCATE_DLQ).build();

        Queue refundApplyQ = QueueBuilder.durable(MqConstant.REFUND_APPLY)
                .deadLetterExchange(MqConstant.DLX)
                .deadLetterRoutingKey(MqConstant.REFUND_APPLY_DLQ)
                .build();
        Queue refundApplyDlq = QueueBuilder.durable(MqConstant.REFUND_APPLY_DLQ).build();

        Queue delayQ = QueueBuilder.durable(MqConstant.ORDER_DELAY)
                .ttl(MqConstant.ORDER_EXPIRE_MINUTES * 60 * 1000)
                .deadLetterExchange(MqConstant.DLX)
                .deadLetterRoutingKey(MqConstant.ORDER_DELAY_CHECK)
                .build();
        Queue checkQ = QueueBuilder.durable(MqConstant.ORDER_DELAY_CHECK).build();

        return new Declarables(
                ex, dlx,
                orderQ, orderDlq,
                stockRestoreQ, stockRestoreDlq,
                seatAllocateQ, seatAllocateDlq,
                refundApplyQ, refundApplyDlq,
                delayQ, checkQ,
                bind(orderQ).to(ex).with(MqConstant.ORDER_CREATE),
                bind(orderDlq).to(dlx).with(MqConstant.ORDER_CREATE_DLQ),
                bind(stockRestoreQ).to(ex).with(MqConstant.STOCK_RESTORE),
                bind(stockRestoreDlq).to(dlx).with(MqConstant.STOCK_RESTORE_DLQ),
                bind(seatAllocateQ).to(ex).with(MqConstant.SEAT_ALLOCATE),
                bind(seatAllocateDlq).to(dlx).with(MqConstant.SEAT_ALLOCATE_DLQ),
                bind(refundApplyQ).to(ex).with(MqConstant.REFUND_APPLY),
                bind(refundApplyDlq).to(dlx).with(MqConstant.REFUND_APPLY_DLQ),
                bind(delayQ).to(ex).with(MqConstant.ORDER_DELAY),
                bind(checkQ).to(dlx).with(MqConstant.ORDER_DELAY_CHECK)
        );
    }
}
