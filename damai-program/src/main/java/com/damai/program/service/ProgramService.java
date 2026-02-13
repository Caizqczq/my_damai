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
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        List<TicketCategory>cats=categoryMapper.selectList(
                new LambdaQueryWrapper<TicketCategory>()
                        .eq(TicketCategory::getProgramId,programId)
        );
        StockDTO stockDTO=new StockDTO();
        stockDTO.setId(programId);
        stockDTO.setCategories(
                cats.stream().map(c->{
                    StockDTO.CategoryStock cs = new StockDTO.CategoryStock();
                    cs.setCategoryId(c.getId());
                    cs.setName(c.getName());
                    cs.setAvailable(c.getAvailableStock());
                    return cs;
                }).toList()
        ); 
        stockDTO.setTotalAvailable(cats.stream().mapToInt(TicketCategory::getAvailableStock).sum());
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
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", 1, TimeUnit.MINUTES);
        if(Boolean.FALSE.equals(isNew)){
            throw new BizException("请勿重复提交");
        }
        boolean stockDeducted = false;
        try {
            TicketCategory category = deductStock(req.getCategoryId(),req.getQuantity());
            stockDeducted = true;
            String programTitle = detail(req.getProgramId()).getTitle();
            if(programTitle==null){
                throw new BizException("节目不存在");
            }

            OrderCreateRequest createReq = new OrderCreateRequest();
            createReq.setUserId(userId);
            createReq.setProgramId(req.getProgramId());
            createReq.setCategoryId(req.getCategoryId());
            createReq.setProgramTitle(programTitle);
            createReq.setCategoryName(category.getName());
            createReq.setUnitPrice(category.getPrice());
            createReq.setQuantity(req.getQuantity().intValue());

            Result<Map<String, Object>> result = orderClient.createOrder(createReq);
            if (result == null || result.getCode() != 200) {
                throw new BizException("创建订单失败");
            }

            return Long.valueOf(result.getData().get("orderId").toString());
        }catch (Exception e){
            redisTemplate.delete(dedupKey);
            if(stockDeducted){
                rollbackStock(req.getCategoryId(),req.getQuantity().intValue());
            }
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
        LambdaUpdateWrapper<TicketCategory> wrapper = new LambdaUpdateWrapper<TicketCategory>()
                .eq(TicketCategory::getId, categoryId)
                .setSql("available_stock = available_stock + " + quantity);
        categoryMapper.update(wrapper);
    }
    
    
}
