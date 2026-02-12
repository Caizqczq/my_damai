package com.damai.program.dto;

import lombok.Data;

import java.util.List;

@Data
public class StockDTO {
    private Long id;
    private List<CategoryStock> categories;
    private Integer totalAvailable;

    @Data
    public static class CategoryStock {
        private Long categoryId;
        private String name;
        private Integer available;
    }
}
