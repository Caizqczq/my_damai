package com.damai.order.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.damai.common.constant.OutboxStatusConstant;
import com.damai.order.entity.OutboxEvent;
import com.damai.order.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventMapper outboxEventMapper;
    private final RabbitTemplate rabbitTemplate;

    public void saveEvent(String exchange, String routingKey, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setId(IdWorker.getId());
        event.setExchangeName(exchange);
        event.setRoutingKey(routingKey);
        event.setPayload(JSON.toJSONString(payload));
        event.setPayloadType(payload.getClass().getName());
        event.setStatus(OutboxStatusConstant.PENDING);
        event.setRetryCount(0);
        event.setNextRetryTime(LocalDateTime.now());
        outboxEventMapper.insert(event);
    }

    public void relayPendingEvents() {
        List<OutboxEvent> events = outboxEventMapper.selectList(new LambdaQueryWrapper<OutboxEvent>()
                .in(OutboxEvent::getStatus, OutboxStatusConstant.PENDING, OutboxStatusConstant.FAILED)
                .le(OutboxEvent::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(OutboxEvent::getId)
                .last("LIMIT 100"));
        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    public void publish(OutboxEvent event) {
        try {
            Class<?> payloadClass = Class.forName(event.getPayloadType());
            Object message = JSON.parseObject(event.getPayload(), payloadClass);
            rabbitTemplate.convertAndSend(event.getExchangeName(), event.getRoutingKey(), message);
            event.setStatus(OutboxStatusConstant.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            outboxEventMapper.updateById(event);
        } catch (Exception e) {
            int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
            event.setStatus(OutboxStatusConstant.FAILED);
            event.setRetryCount(retryCount + 1);
            event.setNextRetryTime(LocalDateTime.now().plusSeconds(Math.min(60, 5L * (retryCount + 1))));
            outboxEventMapper.updateById(event);
            log.error("Outbox发布失败, eventId={}, routingKey={}", event.getId(), event.getRoutingKey(), e);
        }
    }
}
