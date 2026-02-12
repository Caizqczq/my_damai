package com.damai.program.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SeatDTO {
    private String area;
    private List<SeatRow> rows;

    @Data
    public static class SeatRow {
        private String rowNum;
        private List<SeatInfo> seats;
    }

    @Data
    public static class SeatInfo {
        private Long seatId;
        private String col;
        private String label;
        private Integer status;
        private BigDecimal price;
    }
}
