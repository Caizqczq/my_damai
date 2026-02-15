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

    @PostMapping("/releaseSeats")
    public Result<?> releaseSeats(@RequestParam("programId") Long programId,
                                  @RequestParam("categoryId") Long categoryId,
                                  @RequestBody List<Long> seatIds) {
        programService.releaseSeats(programId, categoryId, seatIds);
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
