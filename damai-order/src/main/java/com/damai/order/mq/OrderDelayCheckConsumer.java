package com.damai.order.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.damai.common.constant.MqConstant;
import com.damai.common.mq.SeatOpsMessage;
import com.damai.order.entity.TicketOrder;
import com.damai.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 延迟队列消费者：消息在 order.delay 队列等待 15 分钟后到期，
 * 自动进入 order.delay.check 队列，由此消费者处理超时订单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDelayCheckConsumer {

    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = MqConstant.ORDER_DELAY_CHECK)
    public void onMessage(Map<String, Object> msg) {
        Long orderId = ((Number) msg.get("orderId")).longValue();
        TicketOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        // 只处理未支付的订单
        if (order.getStatus() != 0) {
            return;
        }

        // 取消订单
        order.setStatus(2);
        order.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("延迟队列取消超时订单, orderId={}", orderId);

        // 发 MQ 释放座位
        List<Long> seatIds = parseSeatIds(order.getSeatInfo());
        if (!seatIds.isEmpty()) {
            SeatOpsMessage seatMsg = new SeatOpsMessage();
            seatMsg.setOpsType(SeatOpsMessage.OpsType.RELEASE);
            seatMsg.setProgramId(order.getProgramId());
            seatMsg.setCategoryId(order.getCategoryId());
            seatMsg.setSeatIds(seatIds);
            rabbitTemplate.convertAndSend(MqConstant.EXCHANGE, MqConstant.SEAT_OPS, seatMsg);
        }
    }

    private List<Long> parseSeatIds(String seatInfo) {
        if (seatInfo == null || seatInfo.isBlank()) return List.of();
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
