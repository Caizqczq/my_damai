package com.damai.order.controller;

import com.damai.common.result.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    // TODO: 注入 OrderService

    @PostMapping("/token")
    public Result<?> getToken(@RequestHeader("X-User-Id") Long userId,
                              @RequestParam Long programId) {
        // TODO: 生成抢票幂等Token
        return Result.ok();
    }

    @PostMapping("/grab")
    public Result<?> grab(@RequestHeader("X-User-Id") Long userId,
                          @RequestBody Object grabRequest) {
        // TODO: 抢票核心逻辑（Redis Lua预扣减 + MQ异步下单）
        return Result.ok();
    }

    @GetMapping("/{orderId}")
    public Result<?> detail(@PathVariable Long orderId) {
        // TODO: 查询订单详情
        return Result.ok();
    }

    @PostMapping("/{orderId}/pay")
    public Result<?> pay(@PathVariable Long orderId) {
        // TODO: 模拟支付
        return Result.ok();
    }

    @PostMapping("/{orderId}/cancel")
    public Result<?> cancel(@PathVariable Long orderId) {
        // TODO: 取消订单，回滚库存
        return Result.ok();
    }
}
