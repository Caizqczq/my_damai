package com.damai.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxService outboxService;

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        outboxService.relayPendingEvents();
    }
}
