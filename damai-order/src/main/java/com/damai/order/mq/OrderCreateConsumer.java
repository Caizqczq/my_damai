package com.damai.order.mq;

import com.damai.common.constant.MqConstant;
import com.damai.common.mq.DbSyncMessage;
import com.damai.common.mq.OrderCreateMessage;
import com.damai.order.entity.TicketOrder;
import com.damai.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {

    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = MqConstant.ORDER_CREATE, concurrency = "5-20")
    public void onMessage(OrderCreateMessage msg) {
        try {
            // 幂等：orderId 已存在则跳过
            if (orderMapper.selectById(msg.getOrderId()) != null) {
                log.info("订单已存在, orderId={}, 跳过", msg.getOrderId());
                return;
            }

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
            order.setSeatInfo(msg.getSeatInfo());
            order.setStatus(0);
            order.setExpireTime(LocalDateTime.now().plusMinutes(MqConstant.ORDER_EXPIRE_MINUTES));
            orderMapper.insert(order);

            // 通知 program-service 同步 DB
            DbSyncMessage sync = new DbSyncMessage();
            sync.setBizType(DbSyncMessage.BizType.AFTER_ORDER_CREATED);
            sync.setProgramId(msg.getProgramId());
            sync.setCategoryId(msg.getCategoryId());
            sync.setUserId(msg.getUserId());
            sync.setQuantity(msg.getQuantity());
            sync.setSeatIds(msg.getSeatIds());
            rabbitTemplate.convertAndSend(MqConstant.EXCHANGE, MqConstant.DB_SYNC, sync);

            // 发送延迟检查消息：15 分钟后到期，自动进入 order.delay.check 队列
            // 带上座位信息，消费时直接用，无需再查 DB
            rabbitTemplate.convertAndSend(MqConstant.EXCHANGE, MqConstant.ORDER_DELAY,
                    Map.of("orderId", msg.getOrderId(),
                           "programId", msg.getProgramId(),
                           "categoryId", msg.getCategoryId(),
                           "seatIds", msg.getSeatIds()));
        } catch (Exception e) {
            log.error("订单创建失败, orderId={}, 异常: {}", msg.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }
}
