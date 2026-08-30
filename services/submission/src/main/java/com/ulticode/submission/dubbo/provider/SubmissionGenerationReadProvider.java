package com.ulticode.submission.dubbo.provider;

import com.ulticode.submission.api.service.SubmissionGenerationReadPort;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/** Exposes the current Submission-owner generation to Contest adjudication. */
@DubboService(group = "backend-submission", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionGenerationReadProvider implements SubmissionGenerationReadPort {

    private final SubmissionMapper submissionMapper;

    @Override
    public Long findGenerationForUpdate(String submissionId) {
        return submissionMapper.findGenerationForUpdate(submissionId);
    }
}
