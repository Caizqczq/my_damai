package com.damai.order.client;

import com.damai.common.mq.SeatAllocateRequest;
import com.damai.common.mq.SeatAllocationResult;
import com.damai.common.mq.StockRestoreTaskRequest;
import com.damai.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "damai-program")
public interface ProgramClient {

    @PostMapping("/api/program/internal/reserveDbStock")
    Result<?> reserveDbStock(@RequestParam("categoryId") Long categoryId,
                             @RequestParam("quantity") int quantity);

    @PostMapping("/api/program/internal/allocatePaidSeats")
    Result<SeatAllocationResult> allocatePaidSeats(@RequestBody SeatAllocateRequest req);

    @PostMapping("/api/program/internal/restoreStock")
    Result<?> restoreStock(@RequestParam("programId") Long programId,
                           @RequestParam("categoryId") Long categoryId,
                           @RequestParam("quantity") int quantity);

    @PostMapping("/api/program/internal/restoreStockTask")
    Result<?> restoreStockTask(@RequestBody StockRestoreTaskRequest req);
}
