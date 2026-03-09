package com.damai.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.common.constant.OrderStatusConstant;
import com.damai.order.entity.TicketOrder;
import com.damai.order.mq.OrderCreateConsumer;
import com.damai.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompensationScheduler {

    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final RefundService refundService;

    @Scheduled(fixedDelay = 30000)
    public void scanExpiredOrders() {
        List<TicketOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getStatus, OrderStatusConstant.WAIT_PAY)
                .lt(TicketOrder::getExpireTime, LocalDateTime.now()));
        for (TicketOrder order : orders) {
            orderService.closeOrderAndRestoreStock(order.getId());
        }
    }

    @Scheduled(fixedDelay = 20000)
    public void scanPaidOrdersWithoutSeat() {
        List<TicketOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getStatus, OrderStatusConstant.PAID)
                .eq(TicketOrder::getSeatInfo, OrderCreateConsumer.AUTO_ASSIGN_PENDING));
        LocalDateTime now = LocalDateTime.now();
        for (TicketOrder order : orders) {
            if (order.getPayTime() != null && order.getPayTime().isBefore(now.minusMinutes(5))) {
                refundService.ensureRefund(order, "支付后长时间未分座，系统自动退款");
            } else {
                orderService.sendSeatAllocateTask(order);
            }
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void scanPendingRefunds() {
        refundService.retryPendingRefunds();
    }
}
