package com.damai.order.mq;

import com.damai.common.constant.MqConstant;
import com.damai.common.constant.StockTaskStatusConstant;
import com.damai.common.mq.OrderCreateMessage;
import com.damai.common.mq.StockRestoreTaskRequest;
import com.damai.order.client.ProgramClient;
import com.damai.order.entity.StockTask;
import com.damai.order.service.StockTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateDlqConsumer {

    public static final String SCENE_ORDER_CREATE_DLQ = "ORDER_CREATE_DLQ";

    private final StockTaskService stockTaskService;
    private final ProgramClient programClient;

    @RabbitListener(queues = MqConstant.ORDER_CREATE_DLQ)
    public void onMessage(OrderCreateMessage msg) {
        try {
            StockTask task = stockTaskService.getByOrderId(msg.getOrderId());
            boolean restoreDb = task != null
                    && task.getReserveDbStatus() != null
                    && task.getReserveDbStatus() == StockTaskStatusConstant.RESERVED;

            var result = programClient.restoreStockTask(new StockRestoreTaskRequest(
                    msg.getOrderId(),
                    msg.getProgramId(),
                    msg.getCategoryId(),
                    msg.getQuantity(),
                    SCENE_ORDER_CREATE_DLQ,
                    restoreDb
            ));
            if (result.getCode() != 200) {
                log.error("订单创建DLQ回补失败, orderId={}, message={}", msg.getOrderId(), result.getMessage());
                return;
            }
            log.warn("订单创建DLQ回补完成, orderId={}, restoreDb={}", msg.getOrderId(), restoreDb);
        } catch (Exception e) {
            log.error("订单创建DLQ回补异常, orderId={}", msg.getOrderId(), e);
        }
    }
}
