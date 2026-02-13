package com.damai.program.controller;

import com.damai.common.result.Result;
import com.damai.program.service.ProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/program/internal")
@RequiredArgsConstructor
public class ProgramInternalController {

    private final ProgramService programService;

    @PostMapping("/rollbackStock")
    public Result<?> rollbackStock(@RequestParam("categoryId") Long categoryId,
                                   @RequestParam("quantity") Integer quantity) {
        programService.rollbackStock(categoryId, quantity);
        return Result.ok();
    }

    @PostMapping("/releaseSeats")
    public Result<?> releaseSeats(@RequestBody List<Long> seatIds) {
        programService.releaseSeats(seatIds);
        return Result.ok();
    }

    @PostMapping("/confirmSeats")
    public Result<?> confirmSeats(@RequestBody List<Long> seatIds) {
        programService.confirmSeats(seatIds);
        return Result.ok();
    }
}
