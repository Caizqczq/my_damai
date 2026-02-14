package com.damai.order.task;

import com.damai.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时扫描超时未支付订单，自动取消并释放座位+回补库存
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OrderExpireTask {

    private final OrderService orderService;

    /**
     * 每30秒扫描一次超时未支付的订单
     */
    @Scheduled(fixedDelay = 30_000)
    public void cancelExpiredOrders() {
        try {
            int count = orderService.cancelExpiredOrders();
            if (count > 0) {
                log.info("自动取消超时订单 {} 笔", count);
            }
        } catch (Exception e) {
            log.error("超时订单扫描任务异常", e);
        }
    }
}
