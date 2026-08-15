package com.ulticode.modules.queue.port.adapter;

import com.ulticode.app.api.service.JudgeEnqueuePort;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link JudgeEnqueuePort}, delegating to
 * {@link QueueService}. Both signatures are identical (5-param String).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JudgeEnqueueAdapter implements JudgeEnqueuePort {

    private final QueueService queueService;
    private final FeatureFlagsProperties featureFlags;

    @Override
    public void enqueueJudgeJob(String submissionId, String problemId, String userId,
                                String language, String code) {
        if (featureFlags.isUseJudgeOutbox() && featureFlags.getJudgeQueue().isUsePort()) {
            // The transactional judge_outbox is the sole producer after the
            // Streams cutover. Rejudge/reaper callers still invoke this port
            // for compatibility; routing them to RQueue would duplicate work
            // or bypass the generation fence.
            log.debug("Skipping legacy judge enqueue for submission {}: Streams outbox is active",
                    submissionId);
            return;
        }
        queueService.enqueueJudgeJob(submissionId, problemId, userId, language, code);
    }
}
