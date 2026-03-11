package com.damai.order.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.damai.common.constant.StockTaskStatusConstant;
import com.damai.order.entity.StockTask;
import com.damai.order.mapper.StockTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockTaskService {

    private final StockTaskMapper stockTaskMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initIfAbsent(Long orderId, Long programId, Long categoryId, Integer quantity) {
        if (stockTaskMapper.selectById(orderId) != null) {
            return;
        }
        StockTask task = new StockTask();
        task.setOrderId(orderId);
        task.setProgramId(programId);
        task.setCategoryId(categoryId);
        task.setQuantity(quantity);
        task.setReserveDbStatus(StockTaskStatusConstant.INIT);
        try {
            stockTaskMapper.insert(task);
        } catch (Exception ignored) {
            // 并发重复消费时，主键冲突说明任务已存在，按幂等处理即可。
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReserveDbSuccess(Long orderId) {
        stockTaskMapper.update(null, new LambdaUpdateWrapper<StockTask>()
                .eq(StockTask::getOrderId, orderId)
                .set(StockTask::getReserveDbStatus, StockTaskStatusConstant.RESERVED));
    }

    public StockTask getByOrderId(Long orderId) {
        return stockTaskMapper.selectById(orderId);
    }
}
