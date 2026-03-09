package com.damai.common.mq;

import lombok.Data;

@Data
public class RefundApplyMessage {
    private Long refundId;
    private Long orderId;
}
