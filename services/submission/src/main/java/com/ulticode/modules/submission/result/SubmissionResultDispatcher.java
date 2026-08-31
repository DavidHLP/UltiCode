package com.ulticode.modules.submission.result;

import com.ulticode.modules.submission.result.ResultEventPublisher;
import com.ulticode.common.lifecycle.DrainGate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * Scheduler-driven dispatcher for {@code submission_result_outbox}
 * (SPLIT-003 slice-3, backend-submission local copy).
 *
 * <p>Claims PENDING result events and publishes SubmissionJudged events to
 * the shared {@code stream:integration} Redis stream via
 * Submission owner events are tagged with the canonical `Submission` owner;
 * App-local compatibility events remain tagged `App`.
 * {@link ResultEventPublisher} (DEC-014: no second integration-outbox table;
 * the result row itself is the durable outbox).
 *
 * <p>Separated from {@link SubmissionResultOutboxWriter} to decouple the
 * scheduler from the verdict write path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionResultDispatcher {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;

    private final String claimOwner = "submission-result-" + UUID.randomUUID();

    private final SubmissionResultOutboxMapper resultMapper;
    private final ResultEventPublisher resultEventPublisher;
    private final DrainGate drainGate = new DrainGate();
    @Scheduled(scheduler = "submissionResultOutboxScheduler",
            fixedDelayString = "${result.outbox.dispatcher.interval-ms:3000}",
               initialDelayString = "5000")
    public int dispatch() {
        if (!drainGate.tryEnter()) {
            return 0;
        }
        try {
            return dispatchClaimedBatch();
        } finally {
            drainGate.leave();
        }
    }

    private int dispatchClaimedBatch() {
        resultMapper.reclaimStaleClaimed();
        int claimed = resultMapper.claimPending(claimOwner, BATCH_SIZE);
        if (claimed == 0) {
            return 0;
        }

        List<SubmissionResultOutboxRecord> records = resultMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubmissionResultOutboxRecord>()
                        .eq(SubmissionResultOutboxRecord::getState, "CLAIMED")
                        .eq(SubmissionResultOutboxRecord::getClaimOwner, claimOwner)
                        .orderByAsc(SubmissionResultOutboxRecord::getCreatedAt));

        int published = 0;
        for (SubmissionResultOutboxRecord record : records) {
            try {
                publishResultEvent(record);
                if (resultMapper.markDelivered(record.getId(), claimOwner) > 0) {
                    published++;
                }
            } catch (Exception e) {
                log.error("Failed to dispatch result outbox {}: {}", record.getId(), e.getMessage(), e);
                resultMapper.markFailed(
                        record.getId(), claimOwner, truncate(e.getMessage(), 500), MAX_ATTEMPTS);
            }
        }

        if (published > 0) {
            log.debug("Dispatched {} result outbox events", published);
        }
        return published;
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        drainGate.beginDrain();
    }

    private void publishResultEvent(SubmissionResultOutboxRecord record) {
        long generation = record.getGeneration() == null ? 0L : record.getGeneration();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", record.getSubmissionId());
        payload.put("generation", generation);
        payload.put("userId", record.getUserId());
        payload.put("problemId", record.getProblemId());
        payload.put("verdict", record.getVerdict());
        payload.put("runtimeMs", record.getRuntimeMs());
        payload.put("memoryMb", record.getMemoryMb());
        if (record.getContestId() != null) {
            payload.put("contestId", record.getContestId());
        }

        resultEventPublisher.publish(
                record.getId(),
                "Submission",
                "SubmissionJudged",
                record.getSubmissionId(),
                generation,
                payload);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
