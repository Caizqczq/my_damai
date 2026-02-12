package com.damai.program.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("program")
public class Program {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String artist;
    private String venue;
    private String city;
    private LocalDateTime showTime;
    private LocalDateTime saleTime;
    private String posterUrl;
    private String description;
    private Integer status;
    private Integer totalStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
