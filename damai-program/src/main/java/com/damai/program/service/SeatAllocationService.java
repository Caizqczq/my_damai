package com.damai.program.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.damai.common.exception.BizException;
import com.damai.common.mq.SeatAllocationResult;
import com.damai.program.entity.Seat;
import com.damai.program.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatAllocationService {

    private final SeatMapper seatMapper;

    /**
     * 支付后分座：直接把可用座位更新为已售出，彻底移出抢票热路径。
     */
    @Transactional
    public SeatAllocationResult allocateAfterPay(Long programId, Long categoryId, Long userId, int quantity) {
        List<Seat> availableSeats = seatMapper.selectList(
                new LambdaQueryWrapper<Seat>()
                        .eq(Seat::getProgramId, programId)
                        .eq(Seat::getCategoryId, categoryId)
                        .eq(Seat::getStatus, 0)
                        .orderByAsc(Seat::getArea, Seat::getRowNum, Seat::getColNum)
                        .last("LIMIT " + quantity + " FOR UPDATE SKIP LOCKED"));

        if (availableSeats.size() < quantity) {
            throw new BizException("支付成功，但系统分座失败，需人工兜底");
        }

        List<Long> seatIds = availableSeats.stream().map(Seat::getId).toList();
        int affectedRows = seatMapper.update(new LambdaUpdateWrapper<Seat>()
                .in(Seat::getId, seatIds)
                .eq(Seat::getStatus, 0)
                .set(Seat::getStatus, 2)
                .set(Seat::getLockedBy, userId)
                .set(Seat::getLockedAt, null));

        if (affectedRows < quantity) {
            throw new BizException("支付成功，但座位写入失败，需人工兜底");
        }

        List<Map<String, Object>> seatInfoList = availableSeats.stream().map(seat -> {
            Map<String, Object> item = new HashMap<>();
            item.put("seatId", seat.getId());
            item.put("label", seat.getSeatLabel());
            item.put("area", seat.getArea());
            item.put("row", seat.getRowNum());
            item.put("col", seat.getColNum());
            item.put("price", seat.getPrice());
            return item;
        }).toList();

        SeatAllocationResult result = new SeatAllocationResult();
        result.setSeatIds(seatIds);
        result.setSeatInfoJson(JSON.toJSONString(seatInfoList));
        return result;
    }
}
