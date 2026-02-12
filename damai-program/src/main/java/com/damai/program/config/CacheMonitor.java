package com.damai.program.config;

import com.damai.program.dto.ProgramDetailDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CacheMonitor {

    @Autowired
    private Cache<Long, ProgramDetailDTO> programDetailCache;

    // 每 5 秒打印一次缓存状态
    @Scheduled(fixedRate = 5000)
    public void reportCacheStats() {
        CacheStats stats = programDetailCache.stats();

        log.info("======= Caffeine 缓存监控 =======");
        log.info("命中率: {}%", String.format("%.2f", stats.hitRate() * 100));
        log.info("命中次数: {}", stats.hitCount());
        log.info("缺失次数: {}", stats.missCount());
        log.info("当前缓存大约条数: {}", programDetailCache.estimatedSize());
        log.info("加载成功总耗时: {}ms", stats.totalLoadTime() / 1_000_000); // 纳秒转毫秒
        log.info("===============================");
    }
}
