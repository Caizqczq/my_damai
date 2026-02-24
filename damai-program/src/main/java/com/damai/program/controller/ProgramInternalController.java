package com.damai.program.controller;

import com.damai.common.mq.SeatAllocateRequest;
import com.damai.common.result.Result;
import com.damai.common.mq.SeatAllocationResult;
import com.damai.program.service.ProgramService;
import com.damai.program.service.SeatAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/program/internal")
@RequiredArgsConstructor
public class ProgramInternalController {

    private final ProgramService programService;
    private final SeatAllocationService seatAllocationService;

    @PostMapping("/allocateSeats")
    public Result<SeatAllocationResult> allocateSeats(@RequestBody SeatAllocateRequest req) {
        SeatAllocationResult result = seatAllocationService.allocate(
                req.getProgramId(), req.getCategoryId(), req.getUserId(), req.getQuantity());
        return Result.ok(result);
    }

    @PostMapping("/releaseSeats")
    public Result<?> releaseSeats(@RequestParam("programId") Long programId,
                                  @RequestParam("categoryId") Long categoryId,
                                  @RequestParam("quantity") int quantity,
                                  @RequestBody List<Long> seatIds) {
        programService.releaseSeats(programId, categoryId, seatIds, quantity);
        return Result.ok();
    }

    @PostMapping("/confirmSeats")
    public Result<?> confirmSeats(@RequestParam("programId") Long programId,
                                  @RequestParam("categoryId") Long categoryId,
                                  @RequestBody List<Long> seatIds) {
        programService.confirmSeats(programId, categoryId, seatIds);
        return Result.ok();
    }
}
