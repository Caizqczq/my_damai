package com.damai.common.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatAllocateRequest {
    private Long programId;
    private Long categoryId;
    private Long userId;
    private Integer quantity;
}
