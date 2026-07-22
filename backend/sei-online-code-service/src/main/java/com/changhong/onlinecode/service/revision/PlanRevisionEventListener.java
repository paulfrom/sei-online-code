package com.changhong.onlinecode.service.revision;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs comment-driven plan revisions asynchronously after the comment transaction commits. */
@Component
public class PlanRevisionEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanRevisionEventListener.class);

    private final PlanRevisionOrchestrationService orchestrationService;
    private final PlanRevisionSettlementService settlementService;

    public PlanRevisionEventListener(PlanRevisionOrchestrationService orchestrationService,
                                     PlanRevisionSettlementService settlementService) {
        this.orchestrationService = orchestrationService;
        this.settlementService = settlementService;
    }

    @Async
    @EventListener
    public void onRevisionRequested(PlanRevisionRequestedEvent event) {
        LOGGER.info("Processing plan revision requirementId={}, loopId={}, revisionSeq={}",
                event.requirementId(), event.loopId(), event.revisionSeq());
        orchestrationService.process(event);
    }

    @Async
    @EventListener
    public void onRevisionApplied(PlanRevisionAppliedEvent event) {
        LOGGER.info("Settling applied plan revision requirementId={}, loopId={}, revisionSeq={}",
                event.requirementId(), event.loopId(), event.revisionSeq());
        settlementService.settle(event);
    }
}
