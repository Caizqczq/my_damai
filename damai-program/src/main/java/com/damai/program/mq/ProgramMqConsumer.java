package com.damai.program.mq;

import com.damai.common.constant.MqConstant;
import com.damai.common.mq.OrderCreateMessage;
import com.damai.common.mq.SeatOpsMessage;
import com.damai.program.service.ProgramService;
import com.damai.program.service.StockBucketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * program-service 的所有 MQ 消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramMqConsumer {

    private final ProgramService programService;
    private final StockBucketService stockBucketService;

    /** 座位操作：支付确认 / 取消释放 */
    @RabbitListener(queues = MqConstant.SEAT_OPS)
    public void onSeatOps(SeatOpsMessage msg) {
        switch (msg.getOpsType()) {
            case RELEASE -> programService.releaseSeats(
                    msg.getProgramId(), msg.getCategoryId(), msg.getSeatIds(),
                    msg.getQuantity() != null ? msg.getQuantity() : msg.getSeatIds().size());
            case CONFIRM -> programService.confirmSeats(
                    msg.getProgramId(), msg.getCategoryId(), msg.getSeatIds());
        }
    }

    /** 订单创建 DLQ：回补分桶库存 */
    @RabbitListener(queues = MqConstant.ORDER_CREATE_DLQ)
    public void onOrderCreateDlq(OrderCreateMessage msg) {
        try {
            log.warn("订单创建DLQ, orderId={}, 回补分桶库存", msg.getOrderId());
            stockBucketService.restore(msg.getProgramId(), msg.getCategoryId(), msg.getQuantity());
        } catch (Exception e) {
            log.error("DLQ回补失败, orderId={}, 需人工介入", msg.getOrderId(), e);
        }
    }

    /** 座位操作 DLQ：重试 confirm/release */
    @RabbitListener(queues = MqConstant.SEAT_OPS_DLQ)
    public void onSeatOpsDlq(SeatOpsMessage msg) {
        try {
            log.warn("座位操作DLQ重试, opsType={}, seatIds={}",
                    msg.getOpsType(), msg.getSeatIds());
            switch (msg.getOpsType()) {
                case RELEASE -> programService.releaseSeats(
                        msg.getProgramId(), msg.getCategoryId(), msg.getSeatIds(),
                        msg.getQuantity() != null ? msg.getQuantity() : msg.getSeatIds().size());
                case CONFIRM -> programService.confirmSeats(
                        msg.getProgramId(), msg.getCategoryId(), msg.getSeatIds());
            }
        } catch (Exception e) {
            log.error("【严重】座位操作DLQ重试仍失败, 需人工介入. type={}, seatIds={}",
                    msg.getOpsType(), msg.getSeatIds(), e);
        }
    }
}
