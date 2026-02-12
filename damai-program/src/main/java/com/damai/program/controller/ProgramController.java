package com.damai.program.controller;

import com.damai.common.result.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/program")
public class ProgramController {

    // TODO: 注入 ProgramService

    @GetMapping("/list")
    public Result<?> list(@RequestParam(required = false) String city) {
        // TODO: 实现节目列表查询
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        // TODO: 实现多级缓存查询节目详情
        return Result.ok();
    }

    @GetMapping("/{id}/stock")
    public Result<?> stock(@PathVariable Long id) {
        // TODO: 实现余票查询
        return Result.ok();
    }

    @GetMapping("/{id}/seats")
    public Result<?> seats(@PathVariable Long id) {
        // TODO: 实现座位图查询
        return Result.ok();
    }
}
