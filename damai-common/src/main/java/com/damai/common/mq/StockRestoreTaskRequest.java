package com.damai.common.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StockRestoreTaskRequest {
    private Long orderId;
    private Long programId;
    private Long categoryId;
    private Integer quantity;
    private String scene;
    private Boolean restoreDb;

    public StockRestoreTaskRequest(Long orderId, Long programId, Long categoryId,
                                   Integer quantity, String scene, Boolean restoreDb) {
        this.orderId = orderId;
        this.programId = programId;
        this.categoryId = categoryId;
        this.quantity = quantity;
        this.scene = scene;
        this.restoreDb = restoreDb;
    }
}
