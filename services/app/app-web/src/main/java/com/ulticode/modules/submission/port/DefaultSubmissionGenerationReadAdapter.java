package com.ulticode.modules.submission.port;

import com.ulticode.app.api.service.SubmissionGenerationReadPort;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Owner-side adapter exposing only the source generation needed by contest fencing. */
@Component
@RequiredArgsConstructor
public class DefaultSubmissionGenerationReadAdapter implements SubmissionGenerationReadPort {

    private final SubmissionMapper submissionMapper;

    @Override
    public Long findGenerationForUpdate(String submissionId) {
        return submissionMapper.findGenerationForUpdate(submissionId);
    }
}
