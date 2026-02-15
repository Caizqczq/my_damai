package com.damai.program.strategy;

import java.util.List;

public interface SeatStrategy {

    /**
     * 预留资源：扣减库存 + 锁定座位（原子操作）
     * V2: Redis Lua 扣库存 + DB 事务锁座位
     * V3: 单个 Redis Lua 原子完成两者
     */
    GrabResult reserve(Long programId, Long categoryId, Long userId, int quantity);

    /**
     * 订单创建成功后的异步回写
     * V2: 异步扣减 DB 库存（座位已在 reserve 中同步写入 DB）
     * V3: 异步扣减 DB 库存 + 同步座位状态到 DB
     */
    void afterOrderCreated(Long programId, Long categoryId, Long userId, int quantity, List<Long> seatIds);

    /**
     * 回滚预留（订单创建失败时调用）
     * V2: Redis 回补库存 + DB 释放座位
     * V3: release_seats.lua 原子释放
     */
    void rollbackReserve(Long programId, Long categoryId, List<Long> seatIds);

    /**
     * 释放座位 + 回补库存（取消 / 超时场景，由 OrderService 通过 Feign 调用）
     */
    void releaseSeats(Long programId, Long categoryId, List<Long> seatIds);

    /**
     * 确认售出（支付成功场景，由 OrderService 通过 Feign 调用）
     */
    void confirmSeats(Long programId, Long categoryId, List<Long> seatIds);
}
