package com.ulticode.submission.port.adapter;

import com.ulticode.app.api.service.ContestSubmissionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Contest-association seam for the submission owner (SPLIT-003 slice-2).
 *
 * <p>The {@code contest_submissions} table stays in App; contest association
 * is event/inbox-driven (DEC-011/013) and will arrive with the contest
 * command migration. This owner only serves ordinary (non-contest)
 * submissions, so:
 * <ul>
 *   <li>{@code recordSubmissionIfNeeded} is a no-op — the App routing guard
 *       (CR P1-2) never routes a contest submission here;</li>
 *   <li>{@code findContestId} always returns {@code null} — an ordinary
 *       submission has no contest owner;</li>
 *   <li>virtual-participation queries return false.</li>
 * </ul>
 */
@Slf4j
@Component
public class NoopContestSubmissionPort implements ContestSubmissionPort {

    @Override
    public void recordSubmissionIfNeeded(String submissionId, String userId, Long problemId,
                                         String contestId, String virtualSessionId) {
        if (contestId != null && !contestId.isBlank()) {
            log.warn("Contest submission {} rejected by backend-submission owner "
                    + "(App routing guard must keep contest submissions local)", submissionId);
        }
    }

    @Override
    public boolean isVirtualParticipation(String submissionId) {
        return false;
    }

    @Override
    public boolean isContestSubmission(String submissionId) {
        return false;
    }

    @Override
    public String findContestId(String submissionId) {
        return null;
    }
}
