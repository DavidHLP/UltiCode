package com.ulticode.submission.api.service;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.submission.api.dto.SubmissionVO;

/**
 * Compatibility contract for pre-split consumers.
 *
 * @deprecated New consumers must use {@link SubmissionIntakePort} or
 *             {@link SubmissionVerdictWritePort}. Retain this Interface and
 *             its provider until the mixed-version window is drained.
 */
@Deprecated(forRemoval = true)
public interface SubmissionWritePort extends SubmissionIntakePort, SubmissionVerdictWritePort {

    @Override
    SubmissionVO submit(String userId, CreateSubmissionDTO createDTO);

    @Override
    SubmissionVO submit(String userId, CreateSubmissionDTO createDTO,
                        SubmissionFactsSnapshot facts);

    @Override
    SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO);

    @Override
    SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO,
                               SubmissionFactsSnapshot facts);

    @Override
    void updateSubmissionResult(String submissionId, SubmissionStatus status,
                                int runtime, Double memory, String testDetailsJson);

    @Override
    boolean updateSubmissionResultFenced(String submissionId, SubmissionStatus status,
                                         int runtime, Double memory, String testDetailsJson,
                                         long generation, String attemptId);
}
