package com.damai.order.mq;

import com.damai.common.constant.MqConstant;
import com.damai.common.mq.SeatAllocateRequest;
import com.damai.common.mq.SeatAllocateTaskMessage;
import com.damai.common.mq.SeatAllocationResult;
import com.damai.common.result.Result;
import com.damai.order.client.ProgramClient;
import com.damai.order.entity.TicketOrder;
import com.damai.order.mapper.OrderMapper;
import com.damai.order.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatAllocateConsumer {

    private final OrderMapper orderMapper;
    private final ProgramClient programClient;
    private final RefundService refundService;

    @RabbitListener(queues = MqConstant.SEAT_ALLOCATE, concurrency = "1")
    public void onMessage(SeatAllocateTaskMessage msg) {
        TicketOrder order = orderMapper.selectById(msg.getOrderId());
        if (order == null) {
            log.warn("分座任务对应订单不存在, orderId={}", msg.getOrderId());
            return;
        }
        if (order.getStatus() != 1) {
            log.info("订单非已支付状态，忽略分座任务, orderId={}, status={}", msg.getOrderId(), order.getStatus());
            return;
        }
        if (order.getSeatInfo() != null && !OrderCreateConsumer.AUTO_ASSIGN_PENDING.equals(order.getSeatInfo())) {
            log.info("订单已完成分座, orderId={}", msg.getOrderId());
            return;
        }

        Result<SeatAllocationResult> result = programClient.allocatePaidSeats(
                new SeatAllocateRequest(msg.getProgramId(), msg.getCategoryId(), msg.getUserId(), msg.getQuantity()));
        if (result.getCode() != 200 || result.getData() == null) {
            throw new RuntimeException("支付后分座失败: " + result.getMessage());
        }

        orderMapper.updateSeatInfo(msg.getOrderId(), result.getData().getSeatInfoJson());
        log.info("支付后分座成功, orderId={}", msg.getOrderId());
    }

    @RabbitListener(queues = MqConstant.SEAT_ALLOCATE_DLQ)
    public void onDlq(SeatAllocateTaskMessage msg) {
        TicketOrder order = orderMapper.selectById(msg.getOrderId());
        if (order != null) {
            refundService.ensureRefund(order, "支付后分座失败，系统自动退款");
        }
        log.error("支付后分座DLQ, orderId={}, programId={}, categoryId={}, quantity={}",
                msg.getOrderId(), msg.getProgramId(), msg.getCategoryId(), msg.getQuantity());
    }
}
