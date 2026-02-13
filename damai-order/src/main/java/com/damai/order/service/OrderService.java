package com.damai.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.common.exception.BizException;
import com.damai.order.client.ProgramClient;
import com.damai.order.dto.OrderCreateRequest;
import com.damai.order.entity.TicketOrder;
import com.damai.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderMapper orderMapper;
    private final ProgramClient programClient;

    public Long create(OrderCreateRequest req){
        TicketOrder order = new TicketOrder();
        order.setUserId(req.getUserId());
        order.setProgramId(req.getProgramId());
        order.setCategoryId(req.getCategoryId());
        order.setProgramTitle(req.getProgramTitle());
        order.setCategoryName(req.getCategoryName());
        order.setUnitPrice(req.getUnitPrice());
        order.setQuantity(req.getQuantity());
        order.setTotalAmount(req.getUnitPrice().multiply(BigDecimal.valueOf(req.getQuantity())));
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(15));
        orderMapper.insert(order);
        return order.getId();
    }

    public List<TicketOrder> listByUserId(Long userId) {
        return orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getUserId, userId)
                        .orderByDesc(TicketOrder::getCreatedAt));
    }

    public TicketOrder detail(Long orderId) {
        TicketOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        return order;
    }

    public void pay(Long orderId) {
        TicketOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BizException("订单不存在");
        if (order.getStatus() != 0) throw new BizException("订单状态不允许支付");
        if (order.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException("订单已过期");
        }
        order.setStatus(1);
        order.setPayTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    public void cancel(Long orderId) {
        TicketOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BizException("订单不存在");
        if (order.getStatus() != 0) throw new BizException("订单状态不允许取消");
        order.setStatus(2);
        order.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(order);
        
        try {
            programClient.rollbackStock(order.getCategoryId(), order.getQuantity());
        } catch (Exception e) {
            log.error("回滚库存失败, orderId={}", orderId, e);
        }
    }


}
