package com.ulticode.submission.port.adapter;

import com.ulticode.app.api.service.ContestSubmissionPort;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxMapper;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Contest-association seam for the submission owner (SPLIT-003 slice-2).
 *
 * <p>The {@code contest_submissions} table stays in App; contest association
 * is event/inbox-driven (DEC-011) for remote contest commands. This fallback
 * adapter only serves ordinary (non-contest) submissions, so:
 * <ul>
 *   <li>{@code recordSubmissionIfNeeded} is a no-op — contest commands use
 *       the local owner's durable {@code SubmissionCreated} outbox instead;</li>
 *   <li>contest context needed by result events is read from the durable
 *       {@code SubmissionCreated} row; ordinary submissions still return
 *       {@code null};</li>
 *   <li>virtual-participation queries return false.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoopContestSubmissionPort implements ContestSubmissionPort {

    private final SubmissionCreatedOutboxMapper createdOutboxMapper;

    @Override
    public void recordSubmissionIfNeeded(String submissionId, String userId, Long problemId,
                                         String contestId, String virtualSessionId) {
        if (contestId != null && !contestId.isBlank()) {
            log.warn("Contest submission {} rejected by backend-submission owner "
                    + "(contest commands must use the durable event path)", submissionId);
        }
    }

    @Override
    public boolean isVirtualParticipation(String submissionId) {
        SubmissionCreatedOutboxRecord record =
                createdOutboxMapper.findLatestBySubmissionId(submissionId);
        return record != null && record.getVirtualSessionId() != null
                && !record.getVirtualSessionId().isBlank();
    }

    @Override
    public boolean isContestSubmission(String submissionId) {
        return createdOutboxMapper.findLatestBySubmissionId(submissionId) != null;
    }

    @Override
    public String findContestId(String submissionId) {
        try {
            SubmissionCreatedOutboxRecord record =
                    createdOutboxMapper.findLatestBySubmissionId(submissionId);
            return record == null ? null : record.getContestId();
        } catch (RuntimeException e) {
            log.debug("Unable to resolve contest for submission {}: {}",
                    submissionId, e.getMessage());
            return null;
        }
    }
}
