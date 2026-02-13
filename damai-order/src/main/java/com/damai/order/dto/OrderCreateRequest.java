package com.damai.order.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderCreateRequest {
    private Long userId;
    private Long programId;
    private Long categoryId;
    private String programTitle;
    private String categoryName;
    private BigDecimal unitPrice;
    private Integer quantity;
}
