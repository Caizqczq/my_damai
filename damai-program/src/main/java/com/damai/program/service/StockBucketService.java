package com.damai.program.service;

import com.damai.common.constant.RedisKeyConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class StockBucketService {

    private final StringRedisTemplate redisTemplate;
    private final int bucketSize;
    private final int maxBuckets;
    private final DefaultRedisScript<String> deductScript;

    public StockBucketService(StringRedisTemplate redisTemplate,
                              @Value("${damai.stock.bucket-size:500}") int bucketSize,
                              @Value("${damai.stock.max-buckets:32}") int maxBuckets) {
        this.redisTemplate = redisTemplate;
        this.bucketSize = bucketSize;
        this.maxBuckets = maxBuckets;

        this.deductScript = new DefaultRedisScript<>();
        this.deductScript.setResultType(String.class);
        this.deductScript.setLocation(new ClassPathResource("lua/deduct_bucket.lua"));
    }

    /**
     * 按库存量动态计算桶数：
     *   桶数 = clamp(1, maxBuckets, totalStock / bucketSize)
     *   - 500 张票, bucketSize=500 → 1 桶（无需分桶）
     *   - 2000 张票 → 4 桶
     *   - 50000 张票 → 32 桶（上限）
     */
    private int calcBucketCount(int totalStock) {
        if (totalStock <= 0) return 1;
        return Math.max(1, Math.min(maxBuckets, totalStock / bucketSize));
    }

    /**
     * 初始化分桶：按库存量动态决定桶数，均分库存，桶数写入 Redis
     */
    public void initBuckets(Long programId, Long categoryId, int totalStock) {
        int count = calcBucketCount(totalStock);

        // 先清除旧桶（幂等）
        destroyBuckets(programId, categoryId);

        // 存桶数，deduct/restore/destroy 时读取
        redisTemplate.opsForValue().set(countKey(programId, categoryId), String.valueOf(count));

        int base = totalStock / count;
        int remainder = totalStock % count;
        for (int i = 0; i < count; i++) {
            int stock = base + (i < remainder ? 1 : 0);
            redisTemplate.opsForValue().set(bucketKey(programId, categoryId, i), String.valueOf(stock));
        }
        log.info("分桶初始化: programId={}, categoryId={}, totalStock={}, bucketCount={}, ~{}张/桶",
                programId, categoryId, totalStock, count, base);
    }

    /**
     * 扣减库存：随机起始桶，依次尝试 DECR，最多遍历所有桶
     * <p>
     * 随机起始保证请求均匀散列到各桶，某个桶扣光后自动跳到下一个，
     * 最坏情况（快卖完时）扫描全部桶，代价是 N 次 Redis 调用（N≤32），仍在毫秒级。
     */
    public boolean deduct(Long programId, Long categoryId, int quantity) {
        int count = getBucketCount(programId, categoryId);
        int start = ThreadLocalRandom.current().nextInt(count);
        for (int i = 0; i < count; i++) {
            int idx = (start + i) % count;
            String result = redisTemplate.execute(deductScript,
                    List.of(bucketKey(programId, categoryId, idx)), String.valueOf(quantity));
            if (!"-1".equals(result)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 回补库存：INCRBY 到桶 0（哪个桶不重要，DB 是真正的库存源）
     */
    public void restore(Long programId, Long categoryId, int quantity) {
        redisTemplate.opsForValue().increment(bucketKey(programId, categoryId, 0), quantity);
    }

    /**
     * 清理所有桶 key + count key
     */
    public void destroyBuckets(Long programId, Long categoryId) {
        int count = getBucketCount(programId, categoryId);
        for (int i = 0; i < count; i++) {
            redisTemplate.delete(bucketKey(programId, categoryId, i));
        }
        redisTemplate.delete(countKey(programId, categoryId));
    }

    /**
     * 从 Redis 读取该票档的桶数，key 不存在时返回 1
     */
    private int getBucketCount(Long programId, Long categoryId) {
        String val = redisTemplate.opsForValue().get(countKey(programId, categoryId));
        return val != null ? Integer.parseInt(val) : 1;
    }

    private String countKey(Long programId, Long categoryId) {
        return RedisKeyConstant.STOCK_BUCKET + programId + ":" + categoryId + ":count";
    }

    private String bucketKey(Long programId, Long categoryId, int idx) {
        return RedisKeyConstant.STOCK_BUCKET + programId + ":" + categoryId + ":" + idx;
    }
}
