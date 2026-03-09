package com.damai.common.mq;

import lombok.Data;

@Data
public class StockRestoreMessage {
    private Long orderId;
    private Long programId;
    private Long categoryId;
    private Integer quantity;
}
