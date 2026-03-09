package com.damai.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.common.constant.MqConstant;
import com.damai.common.constant.OrderStatusConstant;
import com.damai.common.exception.BizException;
import com.damai.common.mq.SeatAllocateTaskMessage;
import com.damai.common.mq.StockRestoreMessage;
import com.damai.order.entity.TicketOrder;
import com.damai.order.mapper.OrderMapper;
import com.damai.order.mq.OrderCreateConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderMapper orderMapper;
    private final RefundService refundService;
    private final OutboxService outboxService;

    public List<TicketOrder> listByUserId(Long userId) {
        return orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getUserId, userId)
                        .orderByDesc(TicketOrder::getCreatedAt));
    }

    public TicketOrder detail(Long orderId) {
        TicketOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        return order;
    }

    public void pay(Long orderId) {
        handleMockPaymentSuccess(orderId, "MOCKPAY-" + orderId + "-" + System.currentTimeMillis());
    }

    @Transactional
    public void handleMockPaymentSuccess(Long orderId, String tradeNo) {
        TicketOrder order = detail(orderId);
        LocalDateTime now = LocalDateTime.now();

        if (order.getStatus() == OrderStatusConstant.PAID) {
            log.info("支付回调幂等返回, orderId={}, tradeNo={}", orderId, tradeNo);
            return;
        }
        if (order.getStatus() == OrderStatusConstant.REFUND_PENDING
                || order.getStatus() == OrderStatusConstant.REFUNDED) {
            log.warn("订单已进入退款链路，忽略支付成功回调, orderId={}, tradeNo={}", orderId, tradeNo);
            return;
        }
        if (order.getStatus() == OrderStatusConstant.CLOSED) {
            log.warn("订单已关闭但收到支付成功回调, orderId={}, tradeNo={}, 触发自动退款", orderId, tradeNo);
            refundService.ensureRefund(order, "订单已关闭但支付成功，系统自动退款");
            return;
        }

        if (order.getExpireTime() != null && !order.getExpireTime().isAfter(now)) {
            closeOrderAndRestoreStock(orderId);
            TicketOrder latest = detail(orderId);
            log.warn("支付回调到达时订单已过期关闭, orderId={}, tradeNo={}, 触发自动退款", orderId, tradeNo);
            refundService.ensureRefund(latest, "订单超时关闭后收到支付成功回调，系统自动退款");
            return;
        }

        int rows = orderMapper.payOrder(orderId, now);
        if (rows == 0) {
            TicketOrder latest = detail(orderId);
            if (latest.getStatus() == OrderStatusConstant.CLOSED) {
                refundService.ensureRefund(latest, "订单已关闭但支付成功，系统自动退款");
                return;
            }
            if (latest.getStatus() == OrderStatusConstant.PAID) {
                return;
            }
            throw new BizException("订单支付状态异常");
        }

        sendSeatAllocateTask(detail(orderId));
    }

    @Transactional
    public void cancel(Long orderId) {
        int rows = orderMapper.cancelOrder(orderId, LocalDateTime.now());
        if (rows == 0) {
            TicketOrder order = orderMapper.selectById(orderId);
            if (order == null) throw new BizException("订单不存在");
            if (order.getStatus() == OrderStatusConstant.PAID) throw new BizException("订单已支付，无法取消");
            if (order.getStatus() == OrderStatusConstant.CLOSED) throw new BizException("订单已取消");
            if (order.getStatus() == OrderStatusConstant.REFUND_PENDING) throw new BizException("订单退款处理中");
            throw new BizException("订单状态不允许取消");
        }

        sendStockRestore(orderMapper.selectById(orderId));
    }

    @Transactional
    public void closeOrderAndRestoreStock(Long orderId) {
        int rows = orderMapper.cancelOrder(orderId, LocalDateTime.now());
        if (rows > 0) {
            sendStockRestore(orderMapper.selectById(orderId));
        }
    }

    public void sendSeatAllocateTask(TicketOrder order) {
        SeatAllocateTaskMessage msg = new SeatAllocateTaskMessage();
        msg.setOrderId(order.getId());
        msg.setUserId(order.getUserId());
        msg.setProgramId(order.getProgramId());
        msg.setCategoryId(order.getCategoryId());
        msg.setQuantity(order.getQuantity());
        outboxService.saveEvent(MqConstant.EXCHANGE, MqConstant.SEAT_ALLOCATE, msg);
    }

    public void sendStockRestore(TicketOrder order) {
        StockRestoreMessage msg = new StockRestoreMessage();
        msg.setOrderId(order.getId());
        msg.setProgramId(order.getProgramId());
        msg.setCategoryId(order.getCategoryId());
        msg.setQuantity(order.getQuantity());
        outboxService.saveEvent(MqConstant.EXCHANGE, MqConstant.STOCK_RESTORE, msg);
    }

    public boolean isSeatAssigned(TicketOrder order) {
        return order.getSeatInfo() != null && !OrderCreateConsumer.AUTO_ASSIGN_PENDING.equals(order.getSeatInfo());
    }
}
