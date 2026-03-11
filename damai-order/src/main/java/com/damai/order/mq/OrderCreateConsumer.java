package com.damai.order.mq;

import com.damai.common.constant.MqConstant;
import com.damai.common.mq.OrderCreateMessage;
import com.damai.order.client.ProgramClient;
import com.damai.order.entity.TicketOrder;
import com.damai.order.mapper.OrderMapper;
import com.damai.order.service.OutboxService;
import com.damai.order.service.StockTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {

    public static final String AUTO_ASSIGN_PENDING = "支付成功后系统自动分配座位";

    private final OrderMapper orderMapper;
    private final ProgramClient programClient;
    private final OutboxService outboxService;
    private final StockTaskService stockTaskService;

    @Transactional
    @RabbitListener(queues = MqConstant.ORDER_CREATE, concurrency = "5-20")
    public void onMessage(OrderCreateMessage msg) {
        if (orderMapper.selectById(msg.getOrderId()) != null) {
            log.info("订单已存在, orderId={}, 跳过", msg.getOrderId());
            return;
        }

        stockTaskService.initIfAbsent(msg.getOrderId(), msg.getProgramId(), msg.getCategoryId(), msg.getQuantity());
        var reserveResult = programClient.reserveDbStock(msg.getCategoryId(), msg.getQuantity());
        if (reserveResult.getCode() != 200) {
            throw new RuntimeException("DB库存占用失败: " + reserveResult.getMessage());
        }
        stockTaskService.markReserveDbSuccess(msg.getOrderId());

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
        order.setSeatInfo(AUTO_ASSIGN_PENDING);
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(MqConstant.ORDER_EXPIRE_MINUTES));
        orderMapper.insert(order);

        outboxService.saveEvent(MqConstant.EXCHANGE, MqConstant.ORDER_DELAY,
                Map.of("orderId", msg.getOrderId(),
                        "programId", msg.getProgramId(),
                        "categoryId", msg.getCategoryId(),
                        "quantity", msg.getQuantity()));
    }
}
