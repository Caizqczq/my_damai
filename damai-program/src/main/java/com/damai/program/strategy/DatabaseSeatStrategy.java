package com.damai.program.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.damai.common.constant.RedisKeyConstant;
import com.damai.common.exception.BizException;
import com.damai.program.entity.Seat;
import com.damai.program.entity.TicketCategory;
import com.damai.program.mapper.SeatMapper;
import com.damai.program.mapper.TicketCategoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * V2 策略：Redis Lua 扣库存 + MySQL FOR UPDATE SKIP LOCKED 锁座位
 */
@Slf4j
public class DatabaseSeatStrategy implements SeatStrategy {

    private final StringRedisTemplate redisTemplate;
    private final SeatMapper seatMapper;
    private final TicketCategoryMapper categoryMapper;
    private final TransactionTemplate transactionTemplate;
    private final DefaultRedisScript<String> stockScript;

    public DatabaseSeatStrategy(StringRedisTemplate redisTemplate,
                                SeatMapper seatMapper,
                                TicketCategoryMapper categoryMapper,
                                TransactionTemplate transactionTemplate) {
        this.redisTemplate = redisTemplate;
        this.seatMapper = seatMapper;
        this.categoryMapper = categoryMapper;
        this.transactionTemplate = transactionTemplate;

        this.stockScript = new DefaultRedisScript<>();
        this.stockScript.setResultType(String.class);
        this.stockScript.setLocation(new ClassPathResource("lua/grab_ticket.lua"));
    }

    @Override
    public GrabResult reserve(Long programId, Long categoryId, Long userId, int quantity) {
        // 1. Redis Lua 扣库存
        String stockKey = RedisKeyConstant.PROGRAM_STOCK + programId + ":" + categoryId;
        String luaResult = redisTemplate.execute(stockScript, List.of(stockKey), String.valueOf(quantity));
        if ("-1".equals(luaResult)) {
            throw new BizException("库存不足");
        }

        // 2. DB 事务锁座位
        List<Seat> lockedSeats;
        try {
            lockedSeats = transactionTemplate.execute(status ->
                    doLockSeats(programId, categoryId, userId, quantity));
        } catch (Exception e) {
            // DB 事务失败，回补 Redis 库存
            redisTemplate.opsForValue().increment(stockKey, quantity);
            throw e;
        }

        // 3. 组装结果
        GrabResult result = new GrabResult();
        result.setSeatIds(lockedSeats.stream().map(Seat::getId).toList());
        result.setSeatInfoList(lockedSeats.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("seatId", s.getId());
            m.put("label", s.getSeatLabel());
            m.put("area", s.getArea());
            m.put("row", s.getRowNum());
            m.put("col", s.getColNum());
            m.put("price", s.getPrice());
            return m;
        }).toList());
        return result;
    }

    @Override
    public void afterOrderCreated(Long programId, Long categoryId, Long userId, int quantity, List<Long> seatIds) {
        // V2: 座位已在 reserve 的 DB 事务中写入，只需异步扣减 DB 库存
        CompletableFuture.runAsync(() -> {
            try {
                categoryMapper.update(new LambdaUpdateWrapper<TicketCategory>()
                        .eq(TicketCategory::getId, categoryId)
                        .ge(TicketCategory::getAvailableStock, quantity)
                        .setSql("available_stock = available_stock - " + quantity));
            } catch (Exception e) {
                log.error("V2 异步回写库存失败, categoryId={}, quantity={}", categoryId, quantity, e);
            }
        });
    }

    @Override
    public void rollbackReserve(Long programId, Long categoryId, List<Long> seatIds) {
        // Redis 回补库存 + DB 释放座位
        String stockKey = RedisKeyConstant.PROGRAM_STOCK + programId + ":" + categoryId;
        redisTemplate.opsForValue().increment(stockKey, seatIds.size());
        doReleaseSeatsInDb(seatIds);
    }

    @Override
    public void releaseSeats(Long programId, Long categoryId, List<Long> seatIds) {
        // DB 释放座位
        doReleaseSeatsInDb(seatIds);
        // DB 回补库存
        categoryMapper.update(new LambdaUpdateWrapper<TicketCategory>()
                .eq(TicketCategory::getId, categoryId)
                .setSql("available_stock = available_stock + " + seatIds.size()));
        // Redis 回补库存
        String stockKey = RedisKeyConstant.PROGRAM_STOCK + programId + ":" + categoryId;
        redisTemplate.opsForValue().increment(stockKey, seatIds.size());
    }

    @Override
    public void confirmSeats(Long programId, Long categoryId, List<Long> seatIds) {
        seatMapper.update(new LambdaUpdateWrapper<Seat>()
                .in(Seat::getId, seatIds)
                .eq(Seat::getStatus, 1)
                .set(Seat::getStatus, 2));
    }

    // ==================== 私有方法 ====================

    private List<Seat> doLockSeats(Long programId, Long categoryId, Long userId, int quantity) {
        List<Seat> availableSeats = seatMapper.selectList(
                new LambdaQueryWrapper<Seat>()
                        .eq(Seat::getProgramId, programId)
                        .eq(Seat::getCategoryId, categoryId)
                        .eq(Seat::getStatus, 0)
                        .orderByAsc(Seat::getArea, Seat::getRowNum, Seat::getColNum)
                        .last("LIMIT " + quantity + " FOR UPDATE SKIP LOCKED"));

        if (availableSeats.size() < quantity) {
            throw new BizException("可用座位不足");
        }

        List<Long> seatIds = availableSeats.stream().map(Seat::getId).toList();
        int affectedRows = seatMapper.update(new LambdaUpdateWrapper<Seat>()
                .in(Seat::getId, seatIds)
                .eq(Seat::getStatus, 0)
                .set(Seat::getStatus, 1)
                .set(Seat::getLockedBy, userId)
                .set(Seat::getLockedAt, LocalDateTime.now()));

        if (affectedRows < quantity) {
            throw new BizException("座位锁定失败，请重试");
        }
        return availableSeats;
    }

    private void doReleaseSeatsInDb(List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) return;
        seatMapper.update(new LambdaUpdateWrapper<Seat>()
                .in(Seat::getId, seatIds)
                .set(Seat::getStatus, 0)
                .set(Seat::getLockedBy, null)
                .set(Seat::getLockedAt, null));
    }
}
