package com.ulticode.modules.submission.port;

import com.ulticode.submission.api.service.SubmissionGenerationReadPort;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Owner-side adapter exposing only the source generation needed by contest fencing. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${app.runtime.mode:dev-lite}' == 'legacy-rollback'")
@RequiredArgsConstructor
public class DefaultSubmissionGenerationReadAdapter implements SubmissionGenerationReadPort {

    private final SubmissionMapper submissionMapper;

    @Override
    public Long findGenerationForUpdate(String submissionId) {
        return submissionMapper.findGenerationForUpdate(submissionId);
    }
}
