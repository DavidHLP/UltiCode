package com.ulticode.modules.submission.adapter;

import com.ulticode.modules.admin.service.AdminSubmissionService;
import com.ulticode.modules.submission.dto.BatchRejudgeResponse;
import com.ulticode.modules.submission.dto.RejudgeResult;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.port.SubmissionAdministrationWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LegacySubmissionWriteAdapter implements SubmissionAdministrationWritePort {

    private final SubmissionMapper submissionMapper;
    private final AdminSubmissionService adminSubmissionService;

    @Override
    public Submission selectById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return submissionMapper.selectById(id);
    }

    @Override
    public RejudgeResult rejudgeSubmission(String submissionId, boolean notifyUser) {
        return adminSubmissionService.rejudge(submissionId, notifyUser);
    }

    @Override
    public BatchRejudgeResponse batchRejudgeSubmissions(List<String> submissionIds, boolean notifyUsers) {
        return adminSubmissionService.batchRejudge(submissionIds, notifyUsers);
    }
}
