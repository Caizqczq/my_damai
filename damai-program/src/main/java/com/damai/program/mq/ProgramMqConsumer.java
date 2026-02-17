package com.damai.program.mq;

import com.damai.common.constant.MqConstant;
import com.damai.common.mq.DbSyncMessage;
import com.damai.common.mq.OrderCreateMessage;
import com.damai.common.mq.SeatOpsMessage;
import com.damai.program.service.ProgramService;
import com.damai.program.strategy.SeatStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * program-service 的所有 MQ 消费者（3 个队列合并到一个类）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramMqConsumer {

    private final SeatStrategy seatStrategy;
    private final ProgramService programService;

    /** 订单创建后同步 DB（扣库存 + 锁座位） */
    @RabbitListener(queues = MqConstant.DB_SYNC, concurrency = "5-20")
    public void onDbSync(DbSyncMessage msg) {
        seatStrategy.afterOrderCreated(
                msg.getProgramId(), msg.getCategoryId(),
                msg.getUserId(), msg.getQuantity(), msg.getSeatIds());
    }

    /** 座位操作：支付确认 / 取消释放 */
    @RabbitListener(queues = MqConstant.SEAT_OPS)
    public void onSeatOps(SeatOpsMessage msg) {
        switch (msg.getOpsType()) {
            case RELEASE -> programService.releaseSeats(msg.getProgramId(), msg.getCategoryId(), msg.getSeatIds());
            case CONFIRM -> programService.confirmSeats(msg.getProgramId(), msg.getCategoryId(), msg.getSeatIds());
        }
    }

    /** 订单创建 DLQ：回滚 Redis 座位预留 */
    @RabbitListener(queues = MqConstant.ORDER_CREATE_DLQ)
    public void onOrderCreateDlq(OrderCreateMessage msg) {
        try {
            log.warn("订单创建DLQ, orderId={}, 回滚Redis", msg.getOrderId());
            seatStrategy.rollbackReserve(msg.getProgramId(), msg.getCategoryId(), msg.getSeatIds());
        } catch (Exception e) {
            log.error("DLQ回滚失败, orderId={}, 需人工介入", msg.getOrderId(), e);
        }
    }
}
