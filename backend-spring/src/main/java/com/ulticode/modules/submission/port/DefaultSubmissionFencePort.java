package com.ulticode.modules.submission.port;

import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Default in-process adapter for {@link SubmissionFencePort}.
 *
 * <p>Thin wrapper over {@link SubmissionMapper} that lifts the three fence
 * operations the judge worker needs — read generation, CAS-acquire lease,
 * renew lease — out of raw-mapper + entity territory and into the typed
 * {@code Optional<Long>} / {@code boolean} shape the port promises. This is the
 * only place outside {@code DefaultSubmissionWritePort} that the submission
 * state machine touches {@link SubmissionMapper} for in-flight state; the judge
 * attempt executor now depends on the port instead.
 *
 * <p>Byte-for-byte with the pre-deepening executor: {@code selectById == null}
 * becomes {@link Optional#empty()}, a null {@code generation} column resolves
 * to {@code 1L}, and both CAS ops succeed iff the affected-row count is
 * {@code 1}.
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
