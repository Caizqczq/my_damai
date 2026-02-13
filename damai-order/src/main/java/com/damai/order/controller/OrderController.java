package com.damai.order.controller;

import com.damai.common.result.Result;
import com.damai.order.dto.OrderCreateRequest;
import com.damai.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 内部接口：供 program 服务调用创建订单
     */
    @PostMapping("/internal/create")
    public Result<?> create(@RequestBody OrderCreateRequest createRequest) {
        Long orderId = orderService.create(createRequest);
        return Result.ok(Map.of("orderId",orderId));
    }

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
        return Result.ok();
    }

    @PostMapping("/{orderId}/cancel")
    public Result<?> cancel(@PathVariable Long orderId) {
        orderService.cancel(orderId);
        return Result.ok();
    }
}
