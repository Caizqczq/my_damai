package com.damai.order.mq;

import com.damai.common.constant.MqConstant;
import com.damai.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDelayCheckConsumer {

    private final OrderService orderService;

    @RabbitListener(queues = MqConstant.ORDER_DELAY_CHECK)
    public void onMessage(Map<String, Object> msg) {
        Long orderId = ((Number) msg.get("orderId")).longValue();
        orderService.closeOrderAndRestoreStock(orderId);
        log.info("延迟队列检查完成, orderId={}", orderId);
    }
}
