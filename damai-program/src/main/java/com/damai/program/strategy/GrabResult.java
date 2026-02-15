package com.damai.program.strategy;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GrabResult {
    private List<Long> seatIds;
    private List<Map<String, Object>> seatInfoList;
}
