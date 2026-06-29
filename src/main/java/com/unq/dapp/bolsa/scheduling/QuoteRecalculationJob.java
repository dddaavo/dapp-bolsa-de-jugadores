package com.unq.dapp.bolsa.scheduling;

import com.unq.dapp.bolsa.pricing.application.QuoteRecalculationOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QuoteRecalculationJob {

    private static final Logger log = LoggerFactory.getLogger(QuoteRecalculationJob.class);

    private final QuoteRecalculationOrchestrator orchestrator;

    public QuoteRecalculationJob(QuoteRecalculationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = "${quote.recalc.cron}")
    @SchedulerLock(name = "QuoteRecalculationJob", lockAtMostFor = "PT2H", lockAtLeastFor = "PT1M")
    public void execute() {
        log.info("[QuoteRecalculationJob] Iniciando recalculación de cotizaciones con estrategia default");
        long start = System.currentTimeMillis();
        int total = orchestrator.recalculateAll(null);
        long elapsed = System.currentTimeMillis() - start;
        log.info("[QuoteRecalculationJob] Recalculación completada: {} jugadores actualizados en {}ms", total, elapsed);
    }
}
