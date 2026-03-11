package com.damai.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_task")
public class StockTask {
    @TableId
    private Long orderId;
    private Long programId;
    private Long categoryId;
    private Integer quantity;
    private Integer reserveDbStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
