package com.damai.program.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_restore_record")
public class StockRestoreRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private String scene;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
