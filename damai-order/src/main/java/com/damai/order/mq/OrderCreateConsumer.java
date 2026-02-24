package com.damai.order.mq;

import com.damai.common.constant.MqConstant;
import com.damai.common.mq.OrderCreateMessage;
import com.damai.common.mq.SeatAllocateRequest;
import com.damai.common.mq.SeatAllocationResult;
import com.damai.common.result.Result;
import com.damai.order.client.ProgramClient;
import com.damai.order.entity.TicketOrder;
import com.damai.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {

    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ProgramClient programClient;

    @RabbitListener(queues = MqConstant.ORDER_CREATE, concurrency = "5-20")
    public void onMessage(OrderCreateMessage msg) {
        // 幂等：orderId 已存在则跳过
        if (orderMapper.selectById(msg.getOrderId()) != null) {
            log.info("订单已存在, orderId={}, 跳过", msg.getOrderId());
            return;
        }

        List<Long> seatIds = null;

        try {
            // 1. Feign 调用分座（MQ 已削峰，DB 扛得住）
            Result<SeatAllocationResult> allocResult = programClient.allocateSeats(
                    new SeatAllocateRequest(msg.getProgramId(), msg.getCategoryId(),
                            msg.getUserId(), msg.getQuantity()));

            if (allocResult.getCode() != 200 || allocResult.getData() == null) {
                throw new RuntimeException("分座失败: " + allocResult.getMessage());
            }

            SeatAllocationResult allocation = allocResult.getData();
            seatIds = allocation.getSeatIds();

            // 2. 创建订单（座位信息一起写入）
            TicketOrder order = new TicketOrder();
            order.setId(msg.getOrderId());
            order.setUserId(msg.getUserId());
            order.setProgramId(msg.getProgramId());
            order.setCategoryId(msg.getCategoryId());
            order.setProgramTitle(msg.getProgramTitle());
            order.setCategoryName(msg.getCategoryName());
            order.setUnitPrice(msg.getUnitPrice());
            order.setQuantity(msg.getQuantity());
            order.setTotalAmount(msg.getUnitPrice().multiply(BigDecimal.valueOf(msg.getQuantity())));
            order.setSeatInfo(allocation.getSeatInfoJson());
            order.setStatus(0);
            order.setExpireTime(LocalDateTime.now().plusMinutes(MqConstant.ORDER_EXPIRE_MINUTES));
            orderMapper.insert(order);

            // 3. 发送延迟消息（带座位信息，超时时直接用）
            rabbitTemplate.convertAndSend(MqConstant.EXCHANGE, MqConstant.ORDER_DELAY,
                    Map.of("orderId", msg.getOrderId(),
                            "programId", msg.getProgramId(),
                            "categoryId", msg.getCategoryId(),
                            "quantity", msg.getQuantity(),
                            "seatIds", seatIds));
        } catch (Exception e) {
            log.error("订单创建失败, orderId={}, 异常: {}", msg.getOrderId(), e.getMessage(), e);
            // 分座成功但建单失败 → 释放座位
            if (seatIds != null && !seatIds.isEmpty()) {
                try {
                    programClient.releaseSeats(msg.getProgramId(), msg.getCategoryId(),
                            msg.getQuantity(), seatIds);
                } catch (Exception releaseEx) {
                    log.error("释放座位失败, orderId={}, seatIds={}", msg.getOrderId(), seatIds, releaseEx);
                }
            }
            throw e; // 进 DLQ, DLQ handler 做桶回补
        }
    }
}
