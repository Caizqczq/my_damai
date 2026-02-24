package com.damai.program.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.damai.common.exception.BizException;
import com.damai.common.mq.SeatAllocationResult;
import com.damai.program.entity.Seat;
import com.damai.program.entity.TicketCategory;
import com.damai.program.mapper.SeatMapper;
import com.damai.program.mapper.TicketCategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatAllocationService {

    private final SeatMapper seatMapper;
    private final TicketCategoryMapper categoryMapper;

    /**
     * 分配座位：DB 事务，SELECT FOR UPDATE SKIP LOCKED + UPDATE status=1
     */
    @Transactional
    public SeatAllocationResult allocate(Long programId, Long categoryId, Long userId, int quantity) {
        // 1. 锁定可用座位
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

        // 2. 更新座位状态为已锁定
        int affectedRows = seatMapper.update(new LambdaUpdateWrapper<Seat>()
                .in(Seat::getId, seatIds)
                .eq(Seat::getStatus, 0)
                .set(Seat::getStatus, 1)
                .set(Seat::getLockedBy, userId)
                .set(Seat::getLockedAt, LocalDateTime.now()));

        if (affectedRows < quantity) {
            throw new BizException("座位锁定失败，请重试");
        }

        // 3. 扣减 DB 库存
        categoryMapper.update(new LambdaUpdateWrapper<TicketCategory>()
                .eq(TicketCategory::getId, categoryId)
                .ge(TicketCategory::getAvailableStock, quantity)
                .setSql("available_stock = available_stock - " + quantity));

        // 4. 组装结果
        List<Map<String, Object>> seatInfoList = availableSeats.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("seatId", s.getId());
            m.put("label", s.getSeatLabel());
            m.put("area", s.getArea());
            m.put("row", s.getRowNum());
            m.put("col", s.getColNum());
            m.put("price", s.getPrice());
            return m;
        }).toList();

        SeatAllocationResult result = new SeatAllocationResult();
        result.setSeatIds(seatIds);
        result.setSeatInfoJson(JSON.toJSONString(seatInfoList));
        return result;
    }

    /**
     * 释放座位：UPDATE status=0 + 回补 available_stock
     */
    @Transactional
    public void release(Long programId, Long categoryId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) return;

        seatMapper.update(new LambdaUpdateWrapper<Seat>()
                .in(Seat::getId, seatIds)
                .set(Seat::getStatus, 0)
                .set(Seat::getLockedBy, null)
                .set(Seat::getLockedAt, null));

        categoryMapper.update(new LambdaUpdateWrapper<TicketCategory>()
                .eq(TicketCategory::getId, categoryId)
                .setSql("available_stock = available_stock + " + seatIds.size()));
    }

    /**
     * 确认售出：UPDATE status=2
     */
    public void confirm(Long programId, Long categoryId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) return;

        seatMapper.update(new LambdaUpdateWrapper<Seat>()
                .in(Seat::getId, seatIds)
                .eq(Seat::getStatus, 1)
                .set(Seat::getStatus, 2));
    }
}
