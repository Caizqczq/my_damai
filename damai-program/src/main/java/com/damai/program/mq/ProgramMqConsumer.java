package com.damai.program.mq;

import com.damai.common.constant.MqConstant;
import com.damai.common.mq.OrderCreateMessage;
import com.damai.common.mq.StockRestoreMessage;
import com.damai.program.service.ProgramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramMqConsumer {

    private final ProgramService programService;

    /** 订单创建失败时回补 Redis 和 DB 库存 */
    @RabbitListener(queues = MqConstant.ORDER_CREATE_DLQ)
    public void onOrderCreateDlq(OrderCreateMessage msg) {
        try {
            log.warn("订单创建DLQ, orderId={}, 回补库存", msg.getOrderId());
            programService.restoreStock(msg.getProgramId(), msg.getCategoryId(), msg.getQuantity());
        } catch (Exception e) {
            log.error("DLQ回补失败, orderId={}, 需人工介入", msg.getOrderId(), e);
        }
    }

    /** 未支付订单取消后回补票档库存 */
    @RabbitListener(queues = MqConstant.STOCK_RESTORE)
    public void onStockRestore(StockRestoreMessage msg) {
        programService.restoreStock(msg.getProgramId(), msg.getCategoryId(), msg.getQuantity());
    }

    /** 回补库存失败，记录人工兜底信息 */
    @RabbitListener(queues = MqConstant.STOCK_RESTORE_DLQ)
    public void onStockRestoreDlq(StockRestoreMessage msg) {
        log.error("库存回补DLQ, orderId={}, programId={}, categoryId={}, quantity={}",
                msg.getOrderId(), msg.getProgramId(), msg.getCategoryId(), msg.getQuantity());
    }
}
