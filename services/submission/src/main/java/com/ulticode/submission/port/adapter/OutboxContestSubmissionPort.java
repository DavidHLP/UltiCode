package com.ulticode.submission.port.adapter;

import com.ulticode.app.api.service.ContestSubmissionPort;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxMapper;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Contest-association seam for the submission owner (SPLIT-003 slice-2).
 *
 * <p>The {@code contest_submissions} table stays in App; contest association
 * is event/inbox-driven (DEC-011) for remote contest commands. This adapter
 * serves ordinary (non-contest) submissions in the distributed profile by
 * reading contest context from the durable {@code SubmissionCreated} row.
 *
 * <ul>
 *   <li>{@code findContestId} reads the contest id from the durable
 *       {@code SubmissionCreated} row; ordinary submissions return
 *       {@code null};</li>
 *   <li>{@code isContestSubmission} and {@code isVirtualParticipation} are
 *       derived from the presence/absence and contents of the
 *       {@code SubmissionCreated} row;</li>
 *   <li>contest writes are handled exclusively by the durable event path
 *       ({@code SubmissionCreated} outbox → App {@code ContestSubmissionAdapter} via
 *       {@code recordSubmissionFromEvent}) — there is no local mutation on
 *       this side.</li>
 * </ul>
 *
 * <p>{@link #findContestId} is fail-closed: a transient read failure
 * propagates so the result-outbox write rolls back and the dispatcher
 * retries, rather than emitting a judged event without contest context.</p>
 *
 * <p>Renamed from {@code NoopContestSubmissionPort} (P3-CONTRACT-005) to
 * reflect that this adapter is not a no-op for the read path — it actively
 * reads contest context from the outbox row, and the mutation method
 * ({@code recordSubmissionIfNeeded}) that previously had a warn-no-op has
 * been removed from the port (P3-CONTRACT-004) because it had zero
 * production callers.</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxContestSubmissionPort implements ContestSubmissionPort {

    private final SubmissionCreatedOutboxMapper createdOutboxMapper;

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
        // Fail-closed: a transient read failure must abort the result-outbox
        // write (rollback → dispatcher retry) instead of silently emitting a
        // judged event without contestId, which would make the App contest
        // consumer skip scoring without any retry. A row genuinely absent
        // still yields null (ordinary submission).
        SubmissionCreatedOutboxRecord record =
                createdOutboxMapper.findLatestBySubmissionId(submissionId);
        return record == null ? null : record.getContestId();
    }
}
