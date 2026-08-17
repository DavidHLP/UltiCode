package com.ulticode.modules.submission.created;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import com.ulticode.common.uuid.UuidGenerator;

/** Writes contest-intake events in the Submission owner's intake transaction. */
@Component
@RequiredArgsConstructor
public class SubmissionCreatedOutboxWriter {

    private final SubmissionCreatedOutboxMapper outboxMapper;
    private final UuidGenerator uuidGenerator;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSubmissionCreated(String submissionId, long generation,
                                        String userId, String problemId, String contestId,
                                        String virtualSessionId, String language,
                                        LocalDateTime occurredAt) {
        outboxMapper.insertIfAbsent(
                uuidGenerator.newId(), submissionId, generation, userId, problemId,
                contestId, virtualSessionId, language, occurredAt);
    }
}
