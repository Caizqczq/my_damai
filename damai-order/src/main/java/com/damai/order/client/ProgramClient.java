package com.damai.order.client;

import com.damai.common.mq.SeatAllocateRequest;
import com.damai.common.mq.SeatAllocationResult;
import com.damai.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "damai-program")
public interface ProgramClient {

    @PostMapping("/api/program/internal/allocateSeats")
    Result<SeatAllocationResult> allocateSeats(@RequestBody SeatAllocateRequest req);

    @PostMapping("/api/program/internal/releaseSeats")
    Result<?> releaseSeats(@RequestParam("programId") Long programId,
                           @RequestParam("categoryId") Long categoryId,
                           @RequestParam("quantity") int quantity,
                           @RequestBody List<Long> seatIds);
}
