package com.damai.program.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProgramDetailDTO {
    private Long id;
    private String title;
    private String artist;
    private String venue;
    private String city;
    private LocalDateTime showTime;
    private LocalDateTime saleTime;
    private String description;
    private Integer status;
    private List<CategoryDTO> categories;

    @Data
    public static class CategoryDTO {
        private Long id;
        private String name;
        private BigDecimal price;
    }
}
