package com.ulticode.modules.submission.service;

import com.ulticode.modules.submission.dto.BatchRejudgeResponse;
import com.ulticode.modules.submission.dto.RejudgeResult;
import com.ulticode.modules.submission.entity.Submission;

import java.util.List;
import java.util.Optional;

public interface SubmissionAdministrationDomainService {
    Optional<Submission> findById(String id);
    RejudgeResult rejudge(String submissionId, boolean notifyUser);
    BatchRejudgeResponse batchRejudge(List<String> submissionIds, boolean notifyUsers);
}
