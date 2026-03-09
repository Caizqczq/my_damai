package com.damai.order.controller;

import com.damai.common.result.Result;
import com.damai.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/my")
    public Result<?> myOrders(@RequestHeader("X-User-Id") Long userId) {
        return Result.ok(orderService.listByUserId(userId));
    }

    @GetMapping("/{orderId}")
    public Result<?> detail(@PathVariable Long orderId) {
        return Result.ok(orderService.detail(orderId));
    }

    @PostMapping("/{orderId}/pay")
    public Result<?> pay(@PathVariable Long orderId) {
        orderService.pay(orderId);
        return Result.ok("已模拟支付成功回调");
    }

    @PostMapping("/{orderId}/mock-pay-callback")
    public Result<?> mockPayCallback(@PathVariable Long orderId,
                                     @RequestParam(value = "tradeNo", required = false) String tradeNo) {
        orderService.handleMockPaymentSuccess(orderId,
                tradeNo != null ? tradeNo : ("MOCKCALLBACK-" + orderId + "-" + System.currentTimeMillis()));
        return Result.ok("已处理模拟第三方支付回调");
    }

    @PostMapping("/{orderId}/cancel")
    public Result<?> cancel(@PathVariable Long orderId) {
        orderService.cancel(orderId);
        return Result.ok();
    }
}
