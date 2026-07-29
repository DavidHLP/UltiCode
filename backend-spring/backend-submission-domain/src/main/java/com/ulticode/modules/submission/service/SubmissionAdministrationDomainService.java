package com.ulticode.modules.submission.service;

import com.ulticode.modules.submission.entity.Submission;

import java.util.List;
import java.util.Optional;

public interface SubmissionAdministrationDomainService {
    Optional<Submission> findById(String id);
    Submission rejudge(String submissionId, boolean notifyUser, String actorId);
    List<Submission> batchRejudge(List<String> submissionIds, boolean notifyUsers, String actorId);
}
