package com.damai.common.mq;

import lombok.Data;

import java.util.List;

@Data
public class SeatAllocationResult {
    private List<Long> seatIds;
    private String seatInfoJson;
}
