package com.ulticode.submission.api.service;

import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.submission.api.dto.SubmissionVO;

/** Submission-owner commands that create a pending submission. */
public interface SubmissionIntakePort {

    SubmissionVO submit(String userId, CreateSubmissionDTO createDTO);

    /** Intake using immutable facts captured by the request owner. */
    SubmissionVO submit(String userId, CreateSubmissionDTO createDTO,
                        SubmissionFactsSnapshot facts);

    /** Contest intake after the Contest owner has completed admission checks. */
    SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO);

    /** Contest intake using immutable facts captured by the request owner. */
    SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO,
                               SubmissionFactsSnapshot facts);
}
