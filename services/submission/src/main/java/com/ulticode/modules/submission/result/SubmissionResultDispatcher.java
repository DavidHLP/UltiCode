package com.ulticode.modules.submission.result;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.outbox.OutboxDispatcher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
@Component
public class SubmissionResultDispatcher {

    private final SubmissionResultOutboxMapper resultMapper;
    private final ResultEventPublisher resultEventPublisher;
    private final OutboxDispatcher<SubmissionResultOutboxRecord> dispatcher;

    public SubmissionResultDispatcher(
            SubmissionResultOutboxMapper resultMapper,
            ResultEventPublisher resultEventPublisher) {
        this.resultMapper = resultMapper;
        this.resultEventPublisher = resultEventPublisher;
        this.dispatcher = new OutboxDispatcher<>(
                "submission-result",
                new OutboxDispatcher.Adapter<>() {
                    @Override
                    public void reclaimStaleClaimed() {
                        SubmissionResultDispatcher.this.resultMapper.reclaimStaleClaimed();
                    }

                    @Override
                    public int claimPending(String claimOwner, int limit) {
                        return SubmissionResultDispatcher.this.resultMapper.claimPending(claimOwner, limit);
                    }

                    @Override
                    public List<SubmissionResultOutboxRecord> selectClaimed(String claimOwner) {
                        return SubmissionResultDispatcher.this.resultMapper.selectList(
                                new LambdaQueryWrapper<SubmissionResultOutboxRecord>()
                                        .eq(SubmissionResultOutboxRecord::getState, "CLAIMED")
                                        .eq(SubmissionResultOutboxRecord::getClaimOwner, claimOwner)
                                        .orderByAsc(SubmissionResultOutboxRecord::getCreatedAt));
                    }

                    @Override
                    public String publish(SubmissionResultOutboxRecord record) {
                        SubmissionResultDispatcher.this.publishResultEvent(record);
                        return null;
                    }

                    @Override
                    public int markDelivered(
                            SubmissionResultOutboxRecord record,
                            String claimOwner,
                            String publicationId) {
                        return SubmissionResultDispatcher.this.resultMapper.markDelivered(
                                record.getId(), claimOwner);
                    }

                    @Override
                    public int markFailed(
                            SubmissionResultOutboxRecord record,
                            String claimOwner,
                            String error,
                            int maxAttempts) {
                        return SubmissionResultDispatcher.this.resultMapper.markFailed(
                                record.getId(), claimOwner, error, maxAttempts);
                    }

                    @Override
                    public String recordId(SubmissionResultOutboxRecord record) {
                        return record.getId();
                    }
                });
    }
    @Scheduled(scheduler = "submissionResultOutboxScheduler",
            fixedDelayString = "${result.outbox.dispatcher.interval-ms:3000}",
               initialDelayString = "5000")
    public int dispatch() {
        return dispatcher.dispatch();
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        dispatcher.beginDrain();
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
}
