package com.damai.program.config;

import com.damai.program.service.ProgramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockWarmUp {

    private final ProgramService programService;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        List<Long> programIds = programService.allOnSaleProgramIds();
        if (programIds.isEmpty()) {
            log.info("没有在售节目，跳过库存预热");
            return;
        }
        for (Long programId : programIds) {
            try {
                programService.initStock(programId);
            } catch (Exception e) {
                log.error("库存预热失败, programId={}", programId, e);
            }
        }
        log.info("库存预热完成, 共 {} 个节目", programIds.size());
    }
}
