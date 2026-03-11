package com.damai.program.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.damai.common.constant.StockRestoreStatusConstant;
import com.damai.common.constant.MqConstant;
import com.damai.common.constant.RedisKeyConstant;
import com.damai.common.exception.BizException;
import com.damai.common.mq.OrderCreateMessage;
import com.damai.program.dto.GrabRequest;
import com.damai.program.dto.ProgramDetailDTO;
import com.damai.program.dto.ProgramListItem;
import com.damai.program.dto.StockDTO;
import com.damai.program.entity.Program;
import com.damai.program.entity.StockRestoreRecord;
import com.damai.program.entity.TicketCategory;
import com.damai.program.mapper.ProgramMapper;
import com.damai.program.mapper.StockRestoreRecordMapper;
import com.damai.program.mapper.TicketCategoryMapper;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final StringRedisTemplate redisTemplate;
    private final Cache<Long, ProgramDetailDTO> programDetailCache;
    private final RabbitTemplate rabbitTemplate;
    private final StockBucketService stockBucketService;
    private final StockRestoreRecordMapper stockRestoreRecordMapper;
    @Value("${damai.grab.dedup-enabled:true}")
    private boolean grabDedupEnabled;
    @Value("${damai.grab.dedup-ttl-seconds:30}")
    private long grabDedupTtlSeconds;

    public void initStock(Long programId) {
        List<TicketCategory> cats = categoryMapper.selectList(
                new LambdaQueryWrapper<TicketCategory>()
                        .eq(TicketCategory::getProgramId, programId)
        );
        Map<String, String> meta = new HashMap<>();
        for (TicketCategory cat : cats) {
            meta.put(cat.getId().toString(), cat.getName());
            // 初始化分桶
            stockBucketService.initBuckets(programId, cat.getId(), cat.getAvailableStock());
        }
        // 票档元信息：categoryId -> name
        String metaKey = RedisKeyConstant.PROGRAM_STOCK + programId + ":meta";
        redisTemplate.opsForHash().putAll(metaKey, meta);
    }

    public List<ProgramListItem> list(String city) {
        LambdaQueryWrapper<Program> wrapper = new LambdaQueryWrapper<>();
        if (city != null && !city.isBlank()) {
            wrapper.eq(Program::getCity, city);
        }
        wrapper.orderByAsc(Program::getShowTime);

        List<Program> programs = programMapper.selectList(wrapper);
        if (programs.isEmpty()) {
            return List.of();
        }

        List<Long> programIds = programs.stream().map(Program::getId).toList();
        Map<Long, List<TicketCategory>> categoryMap = categoryMapper.selectList(
                new LambdaQueryWrapper<TicketCategory>()
                        .in(TicketCategory::getProgramId, programIds)
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

            List<TicketCategory> categories = categoryMap.getOrDefault(program.getId(), List.of());
            categories.stream()
                    .map(TicketCategory::getPrice)
                    .min(BigDecimal::compareTo)
                    .ifPresent(item::setMinPrice);
            result.add(item);
        }
        return result;
    }


    public ProgramDetailDTO detail(Long id) {
        //本地缓存
        ProgramDetailDTO localDto = programDetailCache.getIfPresent(id);
        if (localDto != null) {
            return localDto;
        }
        String cacheKey = RedisKeyConstant.PROGRAM_DETAIL + id;
        //redis 缓存
        String cache = redisTemplate.opsForValue().get(cacheKey);
        if (cache != null) {
            ProgramDetailDTO dto = JSON.parseObject(cache, ProgramDetailDTO.class);
            programDetailCache.put(id, dto);
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

    public StockDTO stock(Long programId) {
        // 直接查 DB
        List<TicketCategory> cats = categoryMapper.selectList(
                new LambdaQueryWrapper<TicketCategory>()
                        .eq(TicketCategory::getProgramId, programId)
        );
        StockDTO stockDTO = new StockDTO();
        stockDTO.setId(programId);
        stockDTO.setCategories(cats.stream().map(c -> {
            StockDTO.CategoryStock cs = new StockDTO.CategoryStock();
            cs.setCategoryId(c.getId());
            cs.setName(c.getName());
            cs.setAvailable(c.getAvailableStock());
            return cs;
        }).toList());
        stockDTO.setTotalAvailable(cats.stream().mapToInt(TicketCategory::getAvailableStock).sum());
        return stockDTO;
    }

    public Long grab(Long userId, GrabRequest req) {
        Long programId = req.getProgramId();
        Long categoryId = req.getCategoryId();
        int quantity = req.getQuantity().intValue();

        String dedupKey = null;
        if (grabDedupEnabled) {
            dedupKey = "order:dedup:" + userId + ":" + programId + ":" + categoryId;
            Boolean accepted = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", grabDedupTtlSeconds, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(accepted)) {
                throw new BizException("请求过于频繁，请稍后再试");
            }
        }

        // ===== Phase 1: 分桶 DECR 扣减库存（极轻量） =====
        boolean deducted;
        try {
            deducted = stockBucketService.deduct(programId, categoryId, quantity);
        } catch (Exception e) {
            clearDedupKey(dedupKey);
            throw e;
        }

        if (!deducted) {
            clearDedupKey(dedupKey);
            throw new BizException("库存不足");
        }

        // 从缓存获取节目和票档信息
        ProgramDetailDTO programDetail = detail(programId);
        ProgramDetailDTO.CategoryDTO categoryDTO = programDetail.getCategories().stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new BizException("票档不存在"));

        // ===== Phase 2: 发消息创建订单（不含座位信息） =====
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

        try {
            // 同步等待 Broker 确认，确保消息不丢
            Boolean confirmed = rabbitTemplate.invoke(operations -> {
                operations.convertAndSend(MqConstant.EXCHANGE, MqConstant.ORDER_CREATE, msg);
                return operations.waitForConfirms(5000);
            });
            if (Boolean.FALSE.equals(confirmed)) {
                throw new RuntimeException("Broker NACK");
            }
        } catch (Exception e) {
            log.error("MQ 发送失败, orderId={}", orderId, e);
            clearDedupKey(dedupKey);
            stockBucketService.restore(programId, categoryId, quantity);
            throw new BizException("系统繁忙，请稍后重试");
        }

        return orderId;
    }

    /**
     * 待支付订单异步占用 DB 库存，保证重启后库存口径不丢。
     */
    public void reserveDbStock(Long categoryId, int quantity) {
        int rows = categoryMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TicketCategory>()
                        .eq(TicketCategory::getId, categoryId)
                        .ge(TicketCategory::getAvailableStock, quantity)
                        .setSql("available_stock = available_stock - " + quantity));
        if (rows == 0) {
            throw new BizException("DB库存不足");
        }
    }

    /**
     * 查询所有在售节目 ID（status=1）
     */
    public List<Long> allOnSaleProgramIds() {
        return programMapper.selectList(
                new LambdaQueryWrapper<Program>().eq(Program::getStatus, 1)
        ).stream().map(Program::getId).toList();
    }

    /**
     * 未支付订单取消时仅回补票档库存，不再回滚具体座位。
     */
    public void restoreStock(Long programId, Long categoryId, int quantity) {
        stockBucketService.restore(programId, categoryId, quantity);
        categoryMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TicketCategory>()
                        .eq(TicketCategory::getId, categoryId)
                        .setSql("available_stock = available_stock + " + quantity));
    }

    @org.springframework.transaction.annotation.Transactional
    public void restoreStockPrecisely(Long orderId, Long programId, Long categoryId, int quantity,
                                      String scene, boolean restoreDb) {
        StockRestoreRecord record = stockRestoreRecordMapper.selectOne(
                new LambdaQueryWrapper<StockRestoreRecord>()
                        .eq(StockRestoreRecord::getOrderId, orderId)
                        .eq(StockRestoreRecord::getScene, scene)
                        .last("LIMIT 1"));
        if (record == null) {
            record = new StockRestoreRecord();
            record.setId(IdWorker.getId());
            record.setOrderId(orderId);
            record.setScene(scene);
            record.setStatus(StockRestoreStatusConstant.INIT);
            try {
                stockRestoreRecordMapper.insert(record);
            } catch (Exception e) {
                record = stockRestoreRecordMapper.selectOne(
                        new LambdaQueryWrapper<StockRestoreRecord>()
                                .eq(StockRestoreRecord::getOrderId, orderId)
                                .eq(StockRestoreRecord::getScene, scene)
                                .last("LIMIT 1"));
            }
        }

        if (record == null || record.getStatus() == StockRestoreStatusConstant.SUCCESS) {
            return;
        }

        int claimed = stockRestoreRecordMapper.update(null, new LambdaUpdateWrapper<StockRestoreRecord>()
                .eq(StockRestoreRecord::getId, record.getId())
                .in(StockRestoreRecord::getStatus, StockRestoreStatusConstant.INIT, StockRestoreStatusConstant.FAILED)
                .set(StockRestoreRecord::getStatus, StockRestoreStatusConstant.PROCESSING));
        if (claimed == 0) {
            return;
        }

        try {
            stockBucketService.restore(programId, categoryId, quantity);
            if (restoreDb) {
                categoryMapper.update(null, new LambdaUpdateWrapper<TicketCategory>()
                        .eq(TicketCategory::getId, categoryId)
                        .setSql("available_stock = available_stock + " + quantity));
            }
            stockRestoreRecordMapper.update(null, new LambdaUpdateWrapper<StockRestoreRecord>()
                    .eq(StockRestoreRecord::getId, record.getId())
                    .set(StockRestoreRecord::getStatus, StockRestoreStatusConstant.SUCCESS));
        } catch (Exception e) {
            stockRestoreRecordMapper.update(null, new LambdaUpdateWrapper<StockRestoreRecord>()
                    .eq(StockRestoreRecord::getId, record.getId())
                    .set(StockRestoreRecord::getStatus, StockRestoreStatusConstant.FAILED));
            throw e;
        }
    }

    private void clearDedupKey(String dedupKey) {
        if (dedupKey != null) {
            redisTemplate.delete(dedupKey);
        }
    }
}
