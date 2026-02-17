package com.damai.program.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.damai.common.constant.MqConstant;
import com.damai.common.constant.RedisKeyConstant;
import com.damai.common.exception.BizException;
import com.damai.common.mq.OrderCreateMessage;
import com.damai.program.dto.*;
import com.damai.program.entity.Program;
import com.damai.program.entity.Seat;
import com.damai.program.entity.TicketCategory;
import com.damai.program.mapper.ProgramMapper;
import com.damai.program.mapper.SeatMapper;
import com.damai.program.mapper.TicketCategoryMapper;
import com.damai.program.strategy.GrabResult;
import com.damai.program.strategy.SeatStrategy;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramService {
    private final ProgramMapper programMapper;
    private final TicketCategoryMapper categoryMapper;
    private final SeatMapper seatMapper;
    private final StringRedisTemplate redisTemplate;
    private final Cache<Long,ProgramDetailDTO> programDetailCache;
    private final RabbitTemplate rabbitTemplate;
    private final SeatStrategy seatStrategy;

    public void initStock(Long programId){
        List<TicketCategory>cats=categoryMapper.selectList(
                new LambdaQueryWrapper<TicketCategory>()
                        .eq(TicketCategory::getProgramId,programId)
        );
        Map<String,String> meta = new HashMap<>();
        for(TicketCategory cat:cats){
            meta.put(cat.getId().toString(), cat.getName());

            // ===== 座位数据预热到 Redis =====
            String availKey  = RedisKeyConstant.SEAT_AVAIL + programId + ":" + cat.getId();
            String lockedKey = RedisKeyConstant.SEAT_LOCKED + programId + ":" + cat.getId();
            String seatMetaKey = RedisKeyConstant.SEAT_META + programId + ":" + cat.getId();
            String soldKey   = RedisKeyConstant.SEAT_SOLD + programId + ":" + cat.getId();

            // 清除旧数据，保证幂等（可重复调用 initStock）
            redisTemplate.delete(List.of(availKey, lockedKey, seatMetaKey, soldKey));

            // 查询该票档下所有座位（按 area、row、col 排序）
            List<Seat> seats = seatMapper.selectList(
                    new LambdaQueryWrapper<Seat>()
                            .eq(Seat::getProgramId, programId)
                            .eq(Seat::getCategoryId, cat.getId())
                            .orderByAsc(Seat::getArea, Seat::getRowNum, Seat::getColNum));

            // ===== Pipeline 批量写入座位状态和元数据 =====
            int batchSize = 2000;
            byte[] availKeyBytes = availKey.getBytes(StandardCharsets.UTF_8);
            byte[] lockedKeyBytes = lockedKey.getBytes(StandardCharsets.UTF_8);
            byte[] soldKeyBytes = soldKey.getBytes(StandardCharsets.UTF_8);
            byte[] seatMetaKeyBytes = seatMetaKey.getBytes(StandardCharsets.UTF_8);

            for (int batchStart = 0; batchStart < seats.size(); batchStart += batchSize) {
                int end = Math.min(batchStart + batchSize, seats.size());
                int offset = batchStart;
                List<Seat> batch = seats.subList(batchStart, end);

                redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    for (int i = 0; i < batch.size(); i++) {
                        Seat seat = batch.get(i);
                        byte[] seatIdBytes = seat.getId().toString().getBytes(StandardCharsets.UTF_8);
                        int globalIndex = offset + i;

                        switch (seat.getStatus()) {
                            case 0 -> connection.zSetCommands().zAdd(availKeyBytes, globalIndex, seatIdBytes);
                            case 1 -> {
                                String lockInfo = seat.getLockedBy() + ":"
                                        + System.currentTimeMillis() + ":" + globalIndex;
                                connection.hashCommands().hSet(lockedKeyBytes, seatIdBytes,
                                        lockInfo.getBytes(StandardCharsets.UTF_8));
                            }
                            case 2 -> connection.setCommands().sAdd(soldKeyBytes, seatIdBytes);
                        }

                        // 座位元数据
                        Map<String, Object> seatInfo = new HashMap<>();
                        seatInfo.put("area", seat.getArea());
                        seatInfo.put("row", seat.getRowNum());
                        seatInfo.put("col", seat.getColNum());
                        seatInfo.put("label", seat.getSeatLabel());
                        seatInfo.put("price", seat.getPrice().toString());
                        connection.hashCommands().hSet(seatMetaKeyBytes, seatIdBytes,
                                JSON.toJSONString(seatInfo).getBytes(StandardCharsets.UTF_8));
                    }
                    return null;
                });
            }
        }
        // 票档元信息：categoryId -> name
        String metaKey = RedisKeyConstant.PROGRAM_STOCK+programId+":meta";
        redisTemplate.opsForHash().putAll(metaKey, meta);
    }

    public List<ProgramListItem>list(String city){
        LambdaQueryWrapper<Program>wrapper = new LambdaQueryWrapper<>();
        if(city!=null&&!city.isBlank()){
            wrapper.eq(Program::getCity,city);
        }
        wrapper.orderByAsc(Program::getShowTime);

        List<Program> programs = programMapper.selectList(wrapper);
        if(programs.isEmpty()){
            return List.of();
        }

        List<Long>programIds = programs.stream().map(Program::getId).toList();
        Map<Long,List<TicketCategory>>categoryMap=categoryMapper.selectList(
                new LambdaQueryWrapper<TicketCategory>()
                        .in(TicketCategory::getProgramId,programIds)
        ).stream()
                .collect(Collectors.groupingBy(TicketCategory::getProgramId));

        List<ProgramListItem> result = new ArrayList<>();

        for (Program program : programs) {
            ProgramListItem item = new ProgramListItem();
            item.setId(program.getId());
            item.setTitle(program.getTitle());
            item.setArtist(program.getArtist());
            item.setVenue(program.getVenue());
            item.setCity(program.getCity());
            item.setShowTime(program.getShowTime());
            item.setSaleTime(program.getSaleTime());
            item.setPosterUrl(program.getPosterUrl());
            item.setStatus(program.getStatus());

            List<TicketCategory> categories = categoryMap.getOrDefault(program.getId(),List.of());
            categories.stream()
                    .map(TicketCategory::getPrice)
                    .min(BigDecimal::compareTo)
                    .ifPresent(item::setMinPrice);
            result.add(item);
        }
        return result;
    }


    public ProgramDetailDTO detail(Long id){
        //本地缓存
        ProgramDetailDTO localDto = programDetailCache.getIfPresent(id);
        if (localDto != null) {
            return localDto;
        }
        String cacheKey= RedisKeyConstant.PROGRAM_DETAIL+id;
        //redis 缓存
        String cache = redisTemplate.opsForValue().get(cacheKey);
        if(cache!=null){
            ProgramDetailDTO dto = JSON.parseObject(cache, ProgramDetailDTO.class);
            programDetailCache.put(id,dto);
            return dto;
        }
        //查数据库
        Program program = programMapper.selectById(id);
        if (program == null) {
            throw new BizException(1001, "节目不存在");
        }

        List<TicketCategory> cats = categoryMapper.selectList(
                new LambdaQueryWrapper<TicketCategory>()
                        .eq(TicketCategory::getProgramId, id));
        //组装数据
        ProgramDetailDTO dto = new ProgramDetailDTO();
        dto.setId(program.getId());
        dto.setTitle(program.getTitle());
        dto.setArtist(program.getArtist());
        dto.setVenue(program.getVenue());
        dto.setCity(program.getCity());
        dto.setShowTime(program.getShowTime());
        dto.setSaleTime(program.getSaleTime());
        dto.setDescription(program.getDescription());
        dto.setStatus(program.getStatus());
        dto.setCategories(cats.stream().map(c -> {
            ProgramDetailDTO.CategoryDTO cd = new ProgramDetailDTO.CategoryDTO();
            cd.setId(c.getId());
            cd.setName(c.getName());
            cd.setPrice(c.getPrice());
            return cd;
        }).toList());
        //回填缓存
        redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(dto), 10, TimeUnit.MINUTES);
        programDetailCache.put(id, dto);

        return dto;
    }

    public StockDTO stock(Long programId){
        String metaKey = RedisKeyConstant.PROGRAM_STOCK+programId+":meta";
        Map<Object,Object> meta = redisTemplate.opsForHash().entries(metaKey);

        // 未预热，降级查MySQL
        if(meta.isEmpty()){
            List<TicketCategory>cats=categoryMapper.selectList(
                    new LambdaQueryWrapper<TicketCategory>()
                            .eq(TicketCategory::getProgramId,programId)
            );
            StockDTO stockDTO=new StockDTO();
            stockDTO.setId(programId);
            stockDTO.setCategories(cats.stream().map(c->{
                StockDTO.CategoryStock cs = new StockDTO.CategoryStock();
                cs.setCategoryId(c.getId());
                cs.setName(c.getName());
                cs.setAvailable(c.getAvailableStock());
                return cs;
            }).toList());
            stockDTO.setTotalAvailable(cats.stream().mapToInt(TicketCategory::getAvailableStock).sum());
            return stockDTO;
        }

        // 已预热，完全从Redis读（用 ZCARD 替代 stockKey）
        StockDTO stockDTO=new StockDTO();
        stockDTO.setId(programId);
        List<StockDTO.CategoryStock> categories = new ArrayList<>();
        for(Map.Entry<Object,Object> entry : meta.entrySet()){
            Long categoryId = Long.parseLong(entry.getKey().toString());
            String name = entry.getValue().toString();
            String availKey = RedisKeyConstant.SEAT_AVAIL + programId + ":" + categoryId;
            Long available = redisTemplate.opsForZSet().zCard(availKey);

            StockDTO.CategoryStock cs = new StockDTO.CategoryStock();
            cs.setCategoryId(categoryId);
            cs.setName(name);
            cs.setAvailable(available != null ? available.intValue() : 0);
            categories.add(cs);
        }
        stockDTO.setCategories(categories);
        stockDTO.setTotalAvailable(categories.stream().mapToInt(StockDTO.CategoryStock::getAvailable).sum());
        return stockDTO;
    }

    public List<SeatDTO>seats(Long programId,Long categoryId){
        LambdaQueryWrapper<Seat> wrapper = new LambdaQueryWrapper<Seat>()
                .eq(Seat::getProgramId, programId);
        if (categoryId != null) {
            wrapper.eq(Seat::getCategoryId, categoryId);
        }
        wrapper.orderByAsc(Seat::getArea, Seat::getRowNum, Seat::getColNum);
        List<Seat> seatList = seatMapper.selectList(wrapper);

        Map<String, Map<String, List<Seat>>> grouped = seatList.stream()
                .collect(Collectors.groupingBy(Seat::getArea,
                        Collectors.groupingBy(Seat::getRowNum)));
        return grouped.entrySet().stream().map(areaEntry -> {
            SeatDTO dto = new SeatDTO();
            dto.setArea(areaEntry.getKey());
            dto.setRows(areaEntry.getValue().entrySet().stream().map(rowEntry -> {
                SeatDTO.SeatRow row = new SeatDTO.SeatRow();
                row.setRowNum(rowEntry.getKey());
                row.setSeats(rowEntry.getValue().stream().map(s -> {
                    SeatDTO.SeatInfo info = new SeatDTO.SeatInfo();
                    info.setSeatId(s.getId());
                    info.setCol(s.getColNum());
                    info.setLabel(s.getSeatLabel());
                    info.setStatus(s.getStatus());
                    info.setPrice(s.getPrice());
                    return info;
                }).toList());
                return row;
            }).toList());
            return dto;
        }).toList();
    }


    public Long grab(Long userId, GrabRequest req){
        Long programId = req.getProgramId();
        Long categoryId = req.getCategoryId();
        int quantity = req.getQuantity().intValue();

        String dedupKey = "order:dedup:" + userId + ":" + programId + ":" + categoryId;
        redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", 1, TimeUnit.MINUTES);

        // ===== Phase 1: 策略预留（扣库存 + 锁座位） =====
        GrabResult grabResult;
        try {
            grabResult = seatStrategy.reserve(programId, categoryId, userId, quantity);
        } catch (Exception e) {
            redisTemplate.delete(dedupKey);
            throw e;
        }

        // 从缓存获取节目和票档信息
        ProgramDetailDTO programDetail = detail(programId);
        ProgramDetailDTO.CategoryDTO categoryDTO = programDetail.getCategories().stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new BizException("票档不存在"));

        // ===== Phase 2: 发消息创建订单 =====
        Long orderId = IdWorker.getId();
        OrderCreateMessage msg = new OrderCreateMessage();
        msg.setOrderId(orderId);
        msg.setUserId(userId);
        msg.setProgramId(programId);
        msg.setCategoryId(categoryId);
        msg.setProgramTitle(programDetail.getTitle());
        msg.setCategoryName(categoryDTO.getName());
        msg.setUnitPrice(categoryDTO.getPrice());
        msg.setQuantity(quantity);
        msg.setSeatInfo(JSON.toJSONString(grabResult.getSeatInfoList()));
        msg.setSeatIds(grabResult.getSeatIds());

        try {
            rabbitTemplate.convertAndSend(MqConstant.EXCHANGE, MqConstant.ORDER_CREATE, msg);
        } catch (Exception e) {
            log.error("MQ 发送失败, orderId={}", orderId, e);
            redisTemplate.delete(dedupKey);
            seatStrategy.rollbackReserve(programId, categoryId, grabResult.getSeatIds());
            throw new BizException("系统繁忙，请稍后重试");
        }

        return orderId;
    }

    /**
     * 释放座位 + 回补库存（取消/超时场景，由 SeatOpsConsumer 通过 MQ 触发）
     */
    public void releaseSeats(Long programId, Long categoryId, List<Long> seatIds) {
        seatStrategy.releaseSeats(programId, categoryId, seatIds);
    }

    /**
     * 确认售出（支付成功场景，由 SeatOpsConsumer 通过 MQ 触发）
     */
    public void confirmSeats(Long programId, Long categoryId, List<Long> seatIds) {
        seatStrategy.confirmSeats(programId, categoryId, seatIds);
    }
}
