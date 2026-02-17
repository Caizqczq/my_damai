package com.damai.common.mq;

import lombok.Data;

import java.util.List;

@Data
public class SeatOpsMessage {

    public enum OpsType {
        RELEASE, CONFIRM
    }

    private OpsType opsType;
    private Long programId;
    private Long categoryId;
    private List<Long> seatIds;
}
