package com.damai.order.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.common.constant.MqConstant;
import com.damai.common.exception.BizException;
import com.damai.common.mq.SeatOpsMessage;
import com.damai.order.dto.OrderCreateRequest;
import com.damai.order.entity.TicketOrder;
import com.damai.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;

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
        order.setSeatInfo(req.getSeatInfo());
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

        // 支付成功后，发送 MQ 消息确认座位
        try {
            List<Long> seatIds = parseSeatIds(order.getSeatInfo());
            if (!seatIds.isEmpty()) {
                SeatOpsMessage msg = new SeatOpsMessage();
                msg.setOpsType(SeatOpsMessage.OpsType.CONFIRM);
                msg.setProgramId(order.getProgramId());
                msg.setCategoryId(order.getCategoryId());
                msg.setSeatIds(seatIds);
                rabbitTemplate.convertAndSend(MqConstant.EXCHANGE, MqConstant.SEAT_OPS, msg);
            }
        } catch (Exception e) {
            log.error("支付成功但发送座位确认消息失败, orderId={}, 需人工处理", orderId, e);
        }
    }

    public void cancel(Long orderId) {
        TicketOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BizException("订单不存在");
        if (order.getStatus() != 0) throw new BizException("订单状态不允许取消");
        order.setStatus(2);
        order.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(order);

        try {
            // 发送 MQ 消息释放座位
            List<Long> seatIds = parseSeatIds(order.getSeatInfo());
            if (!seatIds.isEmpty()) {
                SeatOpsMessage msg = new SeatOpsMessage();
                msg.setOpsType(SeatOpsMessage.OpsType.RELEASE);
                msg.setProgramId(order.getProgramId());
                msg.setCategoryId(order.getCategoryId());
                msg.setSeatIds(seatIds);
                rabbitTemplate.convertAndSend(MqConstant.EXCHANGE, MqConstant.SEAT_OPS, msg);
            }
        } catch (Exception e) {
            log.error("释放座位消息发送失败, orderId={}", orderId, e);
        }
    }

    /**
     * 从订单的 seatInfo JSON 中解析出座位ID列表
     */
    private List<Long> parseSeatIds(String seatInfo) {
        if (seatInfo == null || seatInfo.isBlank()) {
            return List.of();
        }
        try {
            JSONArray array = JSON.parseArray(seatInfo);
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                ids.add(obj.getLong("seatId"));
            }
            return ids;
        } catch (Exception e) {
            log.error("解析seatInfo失败: {}", seatInfo, e);
            return List.of();
        }
    }
}
