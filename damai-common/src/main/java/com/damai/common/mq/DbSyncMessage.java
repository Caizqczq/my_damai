package com.damai.common.mq;

import lombok.Data;

import java.util.List;

@Data
public class DbSyncMessage {

    public enum BizType {
        AFTER_ORDER_CREATED
    }

    private BizType bizType;
    private Long programId;
    private Long categoryId;
    private Long userId;
    private Integer quantity;
    private List<Long> seatIds;
}
