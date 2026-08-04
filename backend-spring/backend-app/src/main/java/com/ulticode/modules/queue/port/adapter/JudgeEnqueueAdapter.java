package com.ulticode.modules.queue.port.adapter;

import com.ulticode.app.api.service.JudgeEnqueuePort;
import com.ulticode.modules.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link JudgeEnqueuePort}, delegating to
 * {@link QueueService}. Both signatures are identical (5-param String).
 */
@Component
@RequiredArgsConstructor
public class JudgeEnqueueAdapter implements JudgeEnqueuePort {

    private final QueueService queueService;

    @Override
    public void enqueueJudgeJob(String submissionId, String problemId, String userId,
                                String language, String code) {
        queueService.enqueueJudgeJob(submissionId, problemId, userId, language, code);
    }
}
