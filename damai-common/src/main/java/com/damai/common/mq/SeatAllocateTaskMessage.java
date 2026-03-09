package com.damai.common.mq;

import lombok.Data;

@Data
public class SeatAllocateTaskMessage {
    private Long orderId;
    private Long userId;
    private Long programId;
    private Long categoryId;
    private Integer quantity;
}
