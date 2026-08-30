package com.ulticode.modules.submission.created;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.submission.api.event.SubmissionLifecycleEventContract;
import com.ulticode.modules.submission.result.ResultEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Publishes SubmissionCreated rows to the shared integration stream. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionCreatedDispatcher {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;

    private final String claimOwner = "submission-created-" + UUID.randomUUID();
    private final SubmissionCreatedOutboxMapper outboxMapper;
    private final ResultEventPublisher eventPublisher;

    @Scheduled(scheduler = "submissionCreatedOutboxScheduler",
               fixedDelayString = "${created.outbox.dispatcher.interval-ms:3000}",
               initialDelayString = "5000")
    public int dispatch() {
        outboxMapper.reclaimStaleClaimed();
        int claimed = outboxMapper.claimPending(claimOwner, BATCH_SIZE);
        if (claimed == 0) {
            return 0;
        }

        List<SubmissionCreatedOutboxRecord> records = outboxMapper.selectList(
                new LambdaQueryWrapper<SubmissionCreatedOutboxRecord>()
                        .eq(SubmissionCreatedOutboxRecord::getState, "CLAIMED")
                        .eq(SubmissionCreatedOutboxRecord::getClaimOwner, claimOwner)
                        .orderByAsc(SubmissionCreatedOutboxRecord::getCreatedAt));
        int published = 0;
        for (SubmissionCreatedOutboxRecord record : records) {
            try {
                publish(record);
                if (outboxMapper.markDelivered(record.getId(), claimOwner) > 0) {
                    published++;
                }
            } catch (Exception e) {
                log.error("Failed to dispatch created outbox {}: {}",
                        record.getId(), e.getMessage(), e);
                outboxMapper.markFailed(record.getId(), claimOwner,
                        truncate(e.getMessage(), 500), MAX_ATTEMPTS);
            }
        }
        return published;
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

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
