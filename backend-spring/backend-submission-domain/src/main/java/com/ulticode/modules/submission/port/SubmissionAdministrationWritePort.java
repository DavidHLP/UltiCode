package com.ulticode.modules.submission.port;

import com.ulticode.modules.submission.dto.BatchRejudgeResponse;
import com.ulticode.modules.submission.dto.RejudgeResult;
import com.ulticode.modules.submission.entity.Submission;

import java.util.List;

public interface SubmissionAdministrationWritePort {
    Submission selectById(String id);
    RejudgeResult rejudgeSubmission(String submissionId, boolean notifyUser);
    BatchRejudgeResponse batchRejudgeSubmissions(List<String> submissionIds, boolean notifyUsers);
}
