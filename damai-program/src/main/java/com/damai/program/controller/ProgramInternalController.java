package com.damai.program.controller;

import com.damai.common.mq.SeatAllocateRequest;
import com.damai.common.mq.SeatAllocationResult;
import com.damai.common.mq.StockRestoreTaskRequest;
import com.damai.common.result.Result;
import com.damai.program.service.ProgramService;
import com.damai.program.service.SeatAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/program/internal")
@RequiredArgsConstructor
public class ProgramInternalController {

    private final ProgramService programService;
    private final SeatAllocationService seatAllocationService;

    @PostMapping("/reserveDbStock")
    public Result<?> reserveDbStock(@RequestParam("categoryId") Long categoryId,
                                    @RequestParam("quantity") int quantity) {
        programService.reserveDbStock(categoryId, quantity);
        return Result.ok();
    }

    @PostMapping("/allocatePaidSeats")
    public Result<SeatAllocationResult> allocatePaidSeats(@RequestBody SeatAllocateRequest req) {
        SeatAllocationResult result = seatAllocationService.allocateAfterPay(
                req.getProgramId(), req.getCategoryId(), req.getUserId(), req.getQuantity());
        return Result.ok(result);
    }

    @PostMapping("/restoreStock")
    public Result<?> restoreStock(@RequestParam("programId") Long programId,
                                  @RequestParam("categoryId") Long categoryId,
                                  @RequestParam("quantity") int quantity) {
        programService.restoreStock(programId, categoryId, quantity);
        return Result.ok();
    }

    @PostMapping("/restoreStockTask")
    public Result<?> restoreStockTask(@RequestBody StockRestoreTaskRequest req) {
        programService.restoreStockPrecisely(req.getOrderId(), req.getProgramId(), req.getCategoryId(),
                req.getQuantity(), req.getScene(), Boolean.TRUE.equals(req.getRestoreDb()));
        return Result.ok();
    }
}
