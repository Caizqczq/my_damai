package com.damai.program.controller;

import com.damai.common.result.Result;
import com.damai.program.service.ProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
