package com.damai.common.mq;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderCreateMessage {
    private Long orderId;
    private Long userId;
    private Long programId;
    private Long categoryId;
    private String programTitle;
    private String categoryName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private String seatInfo;
    private List<Long> seatIds;
}
