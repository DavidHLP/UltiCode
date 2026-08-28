package com.ulticode.submission.dubbo.provider;

import com.ulticode.modules.submission.port.DefaultSubmissionWritePort;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionIntakePort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.context.annotation.Profile;

/** Direct Submission-owner provider for intake commands. */
@DubboService(group = "backend-submission", version = "1.0.0")
@Profile("!test")
@RequiredArgsConstructor
public class SubmissionIntakeProvider implements SubmissionIntakePort {

    private final DefaultSubmissionWritePort delegate;

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        return delegate.submit(userId, createDTO);
    }

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO,
                               SubmissionFactsSnapshot facts) {
        return delegate.submit(userId, createDTO, facts);
    }

    @Override
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO) {
        return delegate.submitContest(userId, createDTO);
    }

    @Override
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO,
                                      SubmissionFactsSnapshot facts) {
        return delegate.submitContest(userId, createDTO, facts);
    }
}
