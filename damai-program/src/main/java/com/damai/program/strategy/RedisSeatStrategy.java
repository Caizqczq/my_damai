package com.damai.program.strategy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * V3 策略：Redis Lua 原子完成 扣库存 + 锁座位，DB 全部异步回写
 */
@Slf4j
public class RedisSeatStrategy implements SeatStrategy {

    private final StringRedisTemplate redisTemplate;
    private final SeatMapper seatMapper;
    private final TicketCategoryMapper categoryMapper;
    private final DefaultRedisScript<String> grabV3Script;
    private final DefaultRedisScript<String> releaseSeatsScript;
    private final DefaultRedisScript<String> confirmSeatsScript;

    public RedisSeatStrategy(StringRedisTemplate redisTemplate,
                             SeatMapper seatMapper,
                             TicketCategoryMapper categoryMapper) {
        this.redisTemplate = redisTemplate;
        this.seatMapper = seatMapper;
        this.categoryMapper = categoryMapper;

        this.grabV3Script = new DefaultRedisScript<>();
        this.grabV3Script.setResultType(String.class);
        this.grabV3Script.setLocation(new ClassPathResource("lua/grab_ticket_v3.lua"));

        this.releaseSeatsScript = new DefaultRedisScript<>();
        this.releaseSeatsScript.setResultType(String.class);
        this.releaseSeatsScript.setLocation(new ClassPathResource("lua/release_seats.lua"));

        this.confirmSeatsScript = new DefaultRedisScript<>();
        this.confirmSeatsScript.setResultType(String.class);
        this.confirmSeatsScript.setLocation(new ClassPathResource("lua/confirm_seats.lua"));
    }

    @Override
    public GrabResult reserve(Long programId, Long categoryId, Long userId, int quantity) {
        String stockKey  = RedisKeyConstant.PROGRAM_STOCK + programId + ":" + categoryId;
        String availKey  = RedisKeyConstant.SEAT_AVAIL + programId + ":" + categoryId;
        String lockedKey = RedisKeyConstant.SEAT_LOCKED + programId + ":" + categoryId;

        // 单次 Lua 原子操作：扣库存 + 锁座位
        String luaResult = redisTemplate.execute(grabV3Script,
                List.of(stockKey, availKey, lockedKey),
                String.valueOf(quantity), String.valueOf(userId), String.valueOf(System.currentTimeMillis()));

        JSONObject json = JSON.parseObject(luaResult);
        int code = json.getIntValue("code");
        if (code == -1) throw new BizException("库存不足");
        if (code == -2) throw new BizException("可用座位不足");

        List<Long> seatIds = json.getJSONArray("seatIds")
                .toJavaList(String.class).stream()
                .map(Long::valueOf).toList();

        // 从 Redis Hash 读座位元数据（不查 DB）
        String metaKey = RedisKeyConstant.SEAT_META + programId + ":" + categoryId;
        List<Map<String, Object>> seatInfoList = new ArrayList<>();
        for (Long seatId : seatIds) {
            String metaJson = (String) redisTemplate.opsForHash().get(metaKey, seatId.toString());
            if (metaJson != null) {
                Map<String, Object> m = JSON.parseObject(metaJson);
                m.put("seatId", seatId);
                seatInfoList.add(m);
            }
        }

        GrabResult result = new GrabResult();
        result.setSeatIds(seatIds);
        result.setSeatInfoList(seatInfoList);
        return result;
    }

    @Override
    public void afterOrderCreated(Long programId, Long categoryId, Long userId, int quantity, List<Long> seatIds) {
        // V3: 异步同步库存 + 座位状态到 DB
        CompletableFuture.runAsync(() -> {
            try {
                // 扣减 DB 库存
                categoryMapper.update(new LambdaUpdateWrapper<TicketCategory>()
                        .eq(TicketCategory::getId, categoryId)
                        .ge(TicketCategory::getAvailableStock, quantity)
                        .setSql("available_stock = available_stock - " + quantity));
                // 锁定 DB 座位
                seatMapper.update(new LambdaUpdateWrapper<Seat>()
                        .in(Seat::getId, seatIds)
                        .eq(Seat::getStatus, 0)
                        .set(Seat::getStatus, 1)
                        .set(Seat::getLockedBy, userId)
                        .set(Seat::getLockedAt, LocalDateTime.now()));
            } catch (Exception e) {
                log.error("V3 异步同步DB失败, programId={}, seatIds={}", programId, seatIds, e);
                // TODO: 推入补偿队列
            }
        });
    }

    @Override
    public void rollbackReserve(Long programId, Long categoryId, List<Long> seatIds) {
        // release_seats.lua 原子释放座位 + 回补库存
        executeReleaseLua(programId, categoryId, seatIds);
    }

    @Override
    public void releaseSeats(Long programId, Long categoryId, List<Long> seatIds) {
        // 1. Redis: Lua 原子释放
        executeReleaseLua(programId, categoryId, seatIds);
        // 2. DB: 异步回写
        CompletableFuture.runAsync(() -> {
            try {
                seatMapper.update(new LambdaUpdateWrapper<Seat>()
                        .in(Seat::getId, seatIds)
                        .set(Seat::getStatus, 0)
                        .set(Seat::getLockedBy, null)
                        .set(Seat::getLockedAt, null));
                categoryMapper.update(new LambdaUpdateWrapper<TicketCategory>()
                        .eq(TicketCategory::getId, categoryId)
                        .setSql("available_stock = available_stock + " + seatIds.size()));
            } catch (Exception e) {
                log.error("V3 异步释放座位DB失败, seatIds={}", seatIds, e);
            }
        });
    }

    @Override
    public void confirmSeats(Long programId, Long categoryId, List<Long> seatIds) {
        // 1. Redis: Lua 确认售出
        String lockedKey = RedisKeyConstant.SEAT_LOCKED + programId + ":" + categoryId;
        String soldKey   = RedisKeyConstant.SEAT_SOLD + programId + ":" + categoryId;
        redisTemplate.execute(confirmSeatsScript,
                List.of(lockedKey, soldKey),
                seatIds.stream().map(String::valueOf).toArray(String[]::new));
        // 2. DB: 异步回写
        CompletableFuture.runAsync(() -> {
            try {
                seatMapper.update(new LambdaUpdateWrapper<Seat>()
                        .in(Seat::getId, seatIds)
                        .eq(Seat::getStatus, 1)
                        .set(Seat::getStatus, 2));
            } catch (Exception e) {
                log.error("V3 异步确认座位DB失败, seatIds={}", seatIds, e);
            }
        });
    }

    private void executeReleaseLua(Long programId, Long categoryId, List<Long> seatIds) {
        String stockKey  = RedisKeyConstant.PROGRAM_STOCK + programId + ":" + categoryId;
        String availKey  = RedisKeyConstant.SEAT_AVAIL + programId + ":" + categoryId;
        String lockedKey = RedisKeyConstant.SEAT_LOCKED + programId + ":" + categoryId;
        redisTemplate.execute(releaseSeatsScript,
                List.of(stockKey, availKey, lockedKey),
                seatIds.stream().map(String::valueOf).toArray(String[]::new));
    }
}
