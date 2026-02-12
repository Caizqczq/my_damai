package com.damai.program.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProgramListItem {
    private Long id;
    private String title;
    private String artist;
    private String venue;
    private String city;
    private LocalDateTime showTime;
    private LocalDateTime saleTime;
    private String posterUrl;
    private Integer status;
    private BigDecimal minPrice;
}
