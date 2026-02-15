package com.damai.program.strategy;

import com.damai.program.mapper.SeatMapper;
import com.damai.program.mapper.TicketCategoryMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class SeatStrategyConfig {

    @Bean
    public SeatStrategy seatStrategy(
            @Value("${damai.seat.strategy:redis}") String strategy,
            StringRedisTemplate redisTemplate,
            SeatMapper seatMapper,
            TicketCategoryMapper categoryMapper,
            TransactionTemplate transactionTemplate) {
        return switch (strategy) {
            case "database" -> new DatabaseSeatStrategy(redisTemplate, seatMapper, categoryMapper, transactionTemplate);
            default -> new RedisSeatStrategy(redisTemplate, seatMapper, categoryMapper);
        };
    }
}
