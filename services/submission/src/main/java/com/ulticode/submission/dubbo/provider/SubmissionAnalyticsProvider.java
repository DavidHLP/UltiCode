package com.ulticode.submission.dubbo.provider;

import com.ulticode.submission.api.dto.LanguageCountDTO;
import com.ulticode.submission.api.dto.StatusCountDTO;
import com.ulticode.submission.api.service.SubmissionAnalyticsPort;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/** Exposes typed Submission-owner status and language analytics. */
@DubboService(group = "backend-submission", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionAnalyticsProvider implements SubmissionAnalyticsPort {

    private final SubmissionMapper submissionMapper;

    @Override
    public List<StatusCountDTO> countByStatus() {
        return safe(submissionMapper.countByStatusTyped());
    }

    @Override
    public List<LanguageCountDTO> countByLanguage() {
        return safe(submissionMapper.countByLanguageTyped());
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
