package com.ulticode.modules.submission.created;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.outbox.OutboxDispatcher;
import com.ulticode.submission.api.event.SubmissionLifecycleEventContract;
import com.ulticode.modules.submission.result.ResultEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Publishes SubmissionCreated rows to the shared integration stream. */
@Component
public class SubmissionCreatedDispatcher {

    private final SubmissionCreatedOutboxMapper outboxMapper;
    private final ResultEventPublisher eventPublisher;
    private final OutboxDispatcher<SubmissionCreatedOutboxRecord> dispatcher;

    public SubmissionCreatedDispatcher(
            SubmissionCreatedOutboxMapper outboxMapper,
            ResultEventPublisher eventPublisher) {
        this.outboxMapper = outboxMapper;
        this.eventPublisher = eventPublisher;
        this.dispatcher = new OutboxDispatcher<>(
                "submission-created",
                new OutboxDispatcher.Adapter<>() {
                    @Override
                    public void reclaimStaleClaimed() {
                        SubmissionCreatedDispatcher.this.outboxMapper.reclaimStaleClaimed();
                    }

                    @Override
                    public int claimPending(String claimOwner, int limit) {
                        return SubmissionCreatedDispatcher.this.outboxMapper.claimPending(claimOwner, limit);
                    }

                    @Override
                    public List<SubmissionCreatedOutboxRecord> selectClaimed(String claimOwner) {
                        return SubmissionCreatedDispatcher.this.outboxMapper.selectList(
                                new LambdaQueryWrapper<SubmissionCreatedOutboxRecord>()
                                        .eq(SubmissionCreatedOutboxRecord::getState, "CLAIMED")
                                        .eq(SubmissionCreatedOutboxRecord::getClaimOwner, claimOwner)
                                        .orderByAsc(SubmissionCreatedOutboxRecord::getCreatedAt));
                    }

                    @Override
                    public String publish(SubmissionCreatedOutboxRecord record) {
                        SubmissionCreatedDispatcher.this.publish(record);
                        return null;
                    }

                    @Override
                    public int markDelivered(
                            SubmissionCreatedOutboxRecord record,
                            String claimOwner,
                            String publicationId) {
                        return SubmissionCreatedDispatcher.this.outboxMapper.markDelivered(
                                record.getId(), claimOwner);
                    }

                    @Override
                    public int markFailed(
                            SubmissionCreatedOutboxRecord record,
                            String claimOwner,
                            String error,
                            int maxAttempts) {
                        return SubmissionCreatedDispatcher.this.outboxMapper.markFailed(
                                record.getId(), claimOwner, error, maxAttempts);
                    }

                    @Override
                    public String recordId(SubmissionCreatedOutboxRecord record) {
                        return record.getId();
                    }
                });
    }

    @Scheduled(scheduler = "submissionCreatedOutboxScheduler",
               fixedDelayString = "${created.outbox.dispatcher.interval-ms:3000}",
               initialDelayString = "5000")
    public int dispatch() {
        return dispatcher.dispatch();
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        dispatcher.beginDrain();
    }

    private void publish(SubmissionCreatedOutboxRecord record) {
        long generation = record.getGeneration() == null ? 1L : record.getGeneration();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(SubmissionLifecycleEventContract.SUBMISSION_ID, record.getSubmissionId());
        payload.put(SubmissionLifecycleEventContract.USER_ID, record.getUserId());
        payload.put(SubmissionLifecycleEventContract.PROBLEM_ID, record.getProblemId());
        payload.put(SubmissionLifecycleEventContract.CONTEST_ID, record.getContestId());
        if (record.getVirtualSessionId() != null) {
            payload.put(SubmissionLifecycleEventContract.VIRTUAL_SESSION_ID,
                    record.getVirtualSessionId());
        }
        payload.put(SubmissionLifecycleEventContract.GENERATION, generation);
        payload.put(SubmissionLifecycleEventContract.LANGUAGE, record.getLanguage());
        payload.put(SubmissionLifecycleEventContract.OCCURRED_AT, record.getOccurredAt());
        eventPublisher.publish(
                record.getId(), SubmissionLifecycleEventContract.OWNER,
                SubmissionLifecycleEventContract.CREATED_EVENT_TYPE,
                record.getSubmissionId(), generation, payload);
    }
}
