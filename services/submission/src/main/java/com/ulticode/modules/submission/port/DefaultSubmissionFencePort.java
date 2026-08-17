package com.ulticode.modules.submission.port;

import com.ulticode.submission.api.service.SubmissionFencePort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Local in-process adapter for {@link SubmissionFencePort}.
 *
 * <p>SPLIT-003 slice-4 copy of the App-owned fence port: thin wrapper over
 * {@link SubmissionMapper} that lifts the three fence operations the judge
 * worker needs — read generation, CAS-acquire lease, renew lease — into the
 * typed {@code Optional<Long>} / {@code boolean} shape the port promises.
 * Used directly by the {@code backend-submission} owner provider after the
 * compatibility forwarder retirement.
 *
 * @author ulticode
 */
@Service
@RequiredArgsConstructor
public class DefaultSubmissionFencePort implements SubmissionFencePort {

    private final SubmissionMapper submissionMapper;

    @Override
    public Optional<Long> currentGeneration(String submissionId) {
        Submission current = submissionMapper.selectById(submissionId);
        if (current == null) {
            return Optional.empty();
        }
        return Optional.of(current.getGeneration() != null ? current.getGeneration() : 1L);
    }

    @Override
    public boolean acquireLease(String submissionId, String attemptId, long generation, long ttlSeconds) {
        return submissionMapper.acquireLease(submissionId, attemptId, generation, ttlSeconds) == 1;
    }

    @Override
    public boolean renewLease(String submissionId, String attemptId, long ttlSeconds) {
        return submissionMapper.renewLease(submissionId, attemptId, ttlSeconds) == 1;
    }
}
