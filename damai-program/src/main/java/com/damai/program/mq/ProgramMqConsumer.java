package com.damai.program.mq;

import com.damai.common.constant.MqConstant;
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

    /** 未支付订单取消后回补票档库存 */
    @RabbitListener(queues = MqConstant.STOCK_RESTORE)
    public void onStockRestore(StockRestoreMessage msg) {
        programService.restoreStockPrecisely(msg.getOrderId(), msg.getProgramId(),
                msg.getCategoryId(), msg.getQuantity(), "ORDER_CLOSE", true);
    }

    /** 回补库存失败，记录人工兜底信息 */
    @RabbitListener(queues = MqConstant.STOCK_RESTORE_DLQ)
    public void onStockRestoreDlq(StockRestoreMessage msg) {
        log.error("库存回补DLQ, orderId={}, programId={}, categoryId={}, quantity={}",
                msg.getOrderId(), msg.getProgramId(), msg.getCategoryId(), msg.getQuantity());
    }
}
