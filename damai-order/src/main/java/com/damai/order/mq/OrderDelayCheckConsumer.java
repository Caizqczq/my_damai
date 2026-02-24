package com.damai.order.mq;

import com.damai.common.constant.MqConstant;
import com.damai.common.mq.SeatOpsMessage;
import com.damai.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 延迟队列消费者：消息在 order.delay 队列等待 15 分钟后到期，
 * 自动进入 order.delay.check 队列，由此消费者处理超时订单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDelayCheckConsumer {

    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = MqConstant.ORDER_DELAY_CHECK)
    public void onMessage(Map<String, Object> msg) {
        Long orderId = ((Number) msg.get("orderId")).longValue();

        // 单条 UPDATE 完成状态转移：status 0→2
        // rows=0 说明已支付或已取消，直接跳过
        int rows = orderMapper.cancelOrder(orderId, LocalDateTime.now());
        if (rows == 0) {
            return;
        }
        log.info("延迟队列取消超时订单, orderId={}", orderId);

        Long programId = ((Number) msg.get("programId")).longValue();
        Long categoryId = ((Number) msg.get("categoryId")).longValue();
        int quantity = ((Number) msg.get("quantity")).intValue();
        List<Long> seatIds = ((List<?>) msg.get("seatIds")).stream()
                .map(o -> ((Number) o).longValue()).toList();

        if (!seatIds.isEmpty()) {
            SeatOpsMessage seatMsg = new SeatOpsMessage();
            seatMsg.setOpsType(SeatOpsMessage.OpsType.RELEASE);
            seatMsg.setProgramId(programId);
            seatMsg.setCategoryId(categoryId);
            seatMsg.setSeatIds(seatIds);
            seatMsg.setQuantity(quantity);
            rabbitTemplate.convertAndSend(MqConstant.EXCHANGE, MqConstant.SEAT_OPS, seatMsg);
        }
    }
}
