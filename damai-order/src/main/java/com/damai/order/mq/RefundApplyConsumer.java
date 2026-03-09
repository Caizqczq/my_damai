package com.damai.order.mq;

import com.damai.common.constant.MqConstant;
import com.damai.common.mq.RefundApplyMessage;
import com.damai.order.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundApplyConsumer {

    private final RefundService refundService;

    @RabbitListener(queues = MqConstant.REFUND_APPLY, concurrency = "1")
    public void onMessage(RefundApplyMessage msg) {
        refundService.processRefund(msg.getRefundId());
    }

    @RabbitListener(queues = MqConstant.REFUND_APPLY_DLQ)
    public void onDlq(RefundApplyMessage msg) {
        refundService.markRefundFailed(msg.getRefundId(), "退款消息进入DLQ，需补偿处理");
        log.error("退款申请DLQ, refundId={}, orderId={}", msg.getRefundId(), msg.getOrderId());
    }
}
