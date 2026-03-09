package com.damai.program.controller;

import com.damai.common.result.Result;
import com.damai.program.dto.GrabRequest;
import com.damai.program.service.ProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/program")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    @GetMapping("/list")
    public Result<?> list(@RequestParam(required = false, value = "city") String city) {
        return Result.ok(programService.list(city));
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return Result.ok(programService.detail(id));
    }

    @GetMapping("/{id}/stock")
    public Result<?> stock(@PathVariable Long id) {
        return Result.ok(programService.stock(id));
    }

    @PostMapping("/grab")
    public Result<?> grab(@RequestHeader("X-User-Id") Long userId,
                          @RequestBody GrabRequest grabRequest) {
        Long orderId = programService.grab(userId, grabRequest);
        return Result.ok(Map.of("orderId", orderId));
    }

    @PostMapping("/{id}/init-stock")
    public Result<?> initStock(@PathVariable Long id) {
        programService.initStock(id);
        return Result.ok("库存预热完成");
    }
}
