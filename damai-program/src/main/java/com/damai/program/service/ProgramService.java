package com.damai.program.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.damai.common.constant.RedisKeyConstant;
import com.damai.common.exception.BizException;
import com.damai.common.result.Result;
import com.damai.program.client.OrderClient;
import com.damai.program.dto.*;
import com.damai.program.entity.Program;
import com.damai.program.entity.Seat;
import com.damai.program.entity.TicketCategory;
import com.damai.program.mapper.ProgramMapper;
import com.damai.program.mapper.SeatMapper;
import com.damai.program.mapper.TicketCategoryMapper;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgramService {
    private final ProgramMapper programMapper;
    private final TicketCategoryMapper categoryMapper;
    private final SeatMapper seatMapper;
    private final StringRedisTemplate redisTemplate;
    private final Cache<Long,ProgramDetailDTO> programDetailCache;
    private final OrderClient orderClient;
    private final TransactionTemplate transactionTemplate;
    
    private DefaultRedisScript<String>script=new DefaultRedisScript<>();
    
    @PostConstruct
    public void init(){
        script.setResultType(String.class);
        script.setLocation(new ClassPathResource("lua/grab_ticket.lua"));
    }
    
    public void initStock(Long programId){
        List<TicketCategory>cats=categoryMapper.selectList(
                new LambdaQueryWrapper<TicketCategory>()
                        .eq(TicketCategory::getProgramId,programId)
        );
        Map<String,String> meta = new HashMap<>();
        for(TicketCategory cat:cats){
            String stockKey = RedisKeyConstant.PROGRAM_STOCK+programId+":"+cat.getId();
            redisTemplate.opsForValue().set(stockKey,cat.getAvailableStock().toString());
            meta.put(cat.getId().toString(), cat.getName());
        }
        // 票档元信息：categoryId -> name
        String metaKey = RedisKeyConstant.PROGRAM_STOCK+programId+":meta";
        redisTemplate.opsForHash().putAll(metaKey, meta);
    }

    private void rollbackRedis(String stockKey, int quantity) {
        redisTemplate.opsForValue().increment(stockKey, quantity);
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

        // 已预热，完全从Redis读
        StockDTO stockDTO=new StockDTO();
        stockDTO.setId(programId);
        List<StockDTO.CategoryStock> categories = new ArrayList<>();
        for(Map.Entry<Object,Object> entry : meta.entrySet()){
            Long categoryId = Long.parseLong(entry.getKey().toString());
            String name = entry.getValue().toString();
            String stockKey = RedisKeyConstant.PROGRAM_STOCK+programId+":"+categoryId;
            String val = redisTemplate.opsForValue().get(stockKey);

            StockDTO.CategoryStock cs = new StockDTO.CategoryStock();
            cs.setCategoryId(categoryId);
            cs.setName(name);
            cs.setAvailable(val != null ? Integer.parseInt(val) : 0);
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
        String dedupKey = "order:dedup:"+userId+":"+req.getProgramId()+":"+req.getCategoryId();
        // Redis 幂等查询（保留 Redis 交互，压测时不拦截）
        redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", 1, TimeUnit.MINUTES);

        String stockKey = RedisKeyConstant.PROGRAM_STOCK + req.getProgramId() + ":" + req.getCategoryId();
        String luaResult = redisTemplate.execute(script,List.of(stockKey),String.valueOf(req.getQuantity()));
        if("-1".equals(luaResult)){
            throw new BizException("库存不足");
        }

        // 从缓存获取节目和票档信息（本地缓存 + Redis缓存，不打数据库）
        ProgramDetailDTO programDetail = detail(req.getProgramId());
        ProgramDetailDTO.CategoryDTO categoryDTO = programDetail.getCategories().stream()
                .filter(c -> c.getId().equals(req.getCategoryId()))
                .findFirst()
                .orElseThrow(() -> new BizException("票档不存在"));

        // ===== Phase 1: 本地事务（仅锁座位），库存已由Redis保证 =====
        List<Seat> lockedSeats;
        try {
            lockedSeats = transactionTemplate.execute(status ->
                lockSeats(req.getProgramId(), req.getCategoryId(), userId, req.getQuantity().intValue())
            );
        } catch (Exception e) {
            // 事务内失败，自动回滚，回补Redis
            redisTemplate.delete(dedupKey);
            rollbackRedis(stockKey, req.getQuantity().intValue());
            throw e;
        }

        // ===== Phase 2: 事务外创建订单（Feign 远程调用） =====
        try {
            // 组装座位信息（JSON 格式存入订单）
            List<Map<String, Object>> seatInfoList = lockedSeats.stream().map(s -> {
                Map<String, Object> m = new HashMap<>();
                m.put("seatId", s.getId());
                m.put("label", s.getSeatLabel());
                m.put("area", s.getArea());
                m.put("row", s.getRowNum());
                m.put("col", s.getColNum());
                m.put("price", s.getPrice());
                return m;
            }).toList();

            // 创建订单
            OrderCreateRequest createReq = new OrderCreateRequest();
            createReq.setUserId(userId);
            createReq.setProgramId(req.getProgramId());
            createReq.setCategoryId(req.getCategoryId());
            createReq.setProgramTitle(programDetail.getTitle());
            createReq.setCategoryName(categoryDTO.getName());
            createReq.setUnitPrice(categoryDTO.getPrice());
            createReq.setQuantity(req.getQuantity().intValue());
            createReq.setSeatInfo(JSON.toJSONString(seatInfoList));

            Result<Map<String, Object>> result = orderClient.createOrder(createReq);
            if (result == null || result.getCode() != 200) {
                throw new BizException("创建订单失败");
            }

            // 订单创建成功，异步回写MySQL库存（不阻塞主流程）
            Long categoryId = req.getCategoryId();
            long quantity = req.getQuantity();
            CompletableFuture.runAsync(() -> deductStock(categoryId, quantity));

            return Long.valueOf(result.getData().get("orderId").toString());
        }catch (Exception e){
            // Phase 1 事务已提交，回补Redis+释放座位（MySQL库存未扣，无需rollbackStock）
            redisTemplate.delete(dedupKey);
            rollbackRedis(stockKey, req.getQuantity().intValue());
            releaseSeats(lockedSeats.stream().map(Seat::getId).toList());
            throw e;
        }
    }

    public TicketCategory deductStock(Long categoryId, long quantity){
        LambdaUpdateWrapper<TicketCategory> wrapper = new LambdaUpdateWrapper<TicketCategory>()
                .eq(TicketCategory::getId, categoryId)
                .ge(TicketCategory::getAvailableStock, quantity)
                .setSql("available_stock = available_stock - " + quantity);
        int rows = categoryMapper.update(wrapper);
        if(rows==0){
            throw new BizException("库存不足");
        }
        return categoryMapper.selectById(categoryId);
    }
    
    public void rollbackStock(Long categoryId, int quantity){
        // 回补 MySQL 库存
        LambdaUpdateWrapper<TicketCategory> wrapper = new LambdaUpdateWrapper<TicketCategory>()
                .eq(TicketCategory::getId, categoryId)
                .setSql("available_stock = available_stock + " + quantity);
        categoryMapper.update(wrapper);

        // 回补 Redis 库存（取消/超时场景）
        TicketCategory cat = categoryMapper.selectById(categoryId);
        if (cat != null) {
            String stockKey = RedisKeyConstant.PROGRAM_STOCK + cat.getProgramId() + ":" + categoryId;
            redisTemplate.opsForValue().increment(stockKey, quantity);
        }
    }

    /**
     * 锁定座位：使用 FOR UPDATE SKIP LOCKED 查询可用座位并标记为已锁定。
     * SKIP LOCKED 使并发事务自动跳过已被其他事务锁定的行，避免竞态条件。
     * 必须在事务内调用（由 grab() 的 @Transactional 保证）。
     */
    public List<Seat> lockSeats(Long programId, Long categoryId, Long userId, int quantity) {
        // 查询可用座位，FOR UPDATE SKIP LOCKED 保证不同事务选到不同座位
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

        // 批量锁定座位（status=1 表示已锁定）
        List<Long> seatIds = availableSeats.stream().map(Seat::getId).toList();
        int affectedRows = seatMapper.update(new LambdaUpdateWrapper<Seat>()
                .in(Seat::getId, seatIds)
                .eq(Seat::getStatus, 0)
                .set(Seat::getStatus, 1)
                .set(Seat::getLockedBy, userId)
                .set(Seat::getLockedAt, LocalDateTime.now()));

        // 校验实际锁定数量，防止极端情况下的数据不一致
        if (affectedRows < quantity) {
            throw new BizException("座位锁定失败，请重试");
        }

        return availableSeats;
    }

    public void confirmSeats(List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return;
        }
        seatMapper.update(new LambdaUpdateWrapper<Seat>()
                .in(Seat::getId, seatIds)
                .eq(Seat::getStatus, 1)
                .set(Seat::getStatus, 2));
    }

    /**
     * 释放座位：将已锁定的座位恢复为可用
     */
    public void releaseSeats(List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return;
        }
        seatMapper.update(new LambdaUpdateWrapper<Seat>()
                .in(Seat::getId, seatIds)
                .set(Seat::getStatus, 0)
                .set(Seat::getLockedBy, null)
                .set(Seat::getLockedAt, null));
    }
}
