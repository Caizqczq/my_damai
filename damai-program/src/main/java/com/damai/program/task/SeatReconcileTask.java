package com.damai.program.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.common.constant.RedisKeyConstant;
import com.damai.program.entity.Seat;
import com.damai.program.entity.TicketCategory;
import com.damai.program.mapper.SeatMapper;
import com.damai.program.mapper.TicketCategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatReconcileTask {

    private final TicketCategoryMapper categoryMapper;
    private final SeatMapper seatMapper;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void reconcile() {
        List<TicketCategory> categories = categoryMapper.selectList(null);
        for (TicketCategory cat : categories) {
            try {
                String availKey = RedisKeyConstant.SEAT_AVAIL + cat.getProgramId() + ":" + cat.getId();
                Long redisAvail = redisTemplate.opsForZSet().zCard(availKey);
                if (redisAvail == null) {
                    continue;
                }

                long dbAvail = seatMapper.selectCount(
                        new LambdaQueryWrapper<Seat>()
                                .eq(Seat::getProgramId, cat.getProgramId())
                                .eq(Seat::getCategoryId, cat.getId())
                                .eq(Seat::getStatus, 0));

                if (redisAvail != dbAvail) {
                    log.warn("库存不一致! programId={}, categoryId={}, redisAvail={}, dbAvail={}",
                            cat.getProgramId(), cat.getId(), redisAvail, dbAvail);
                }
            } catch (Exception e) {
                log.error("对账异常, categoryId={}", cat.getId(), e);
            }
        }
    }
}
