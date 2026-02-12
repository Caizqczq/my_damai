package com.damai.program.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("program_ticket_category")
public class TicketCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long programId;
    private String name;
    private BigDecimal price;
    private Integer totalStock;
    private Integer availableStock;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
