package com.ulticode.modules.contest.port.adapter;

import com.ulticode.submission.api.dto.SubmissionAdjudicationFact;
import com.ulticode.submission.api.service.SubmissionAdjudicationReadPort;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** Explicit legacy-rollback adapter for local Submission fact reads. */
@Component
@ConditionalOnExpression("'${app.runtime.mode:dev-lite}' == 'legacy-rollback'")
@RequiredArgsConstructor
public class LocalSubmissionAdjudicationReadAdapter implements SubmissionAdjudicationReadPort {

    private final SubmissionMapper submissionMapper;

    @Override
    public List<SubmissionAdjudicationFact> findByIds(Collection<String> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return List.of();
        }
        List<SubmissionAdjudicationFact> facts = submissionMapper.findAdjudicationFactsByIds(submissionIds);
        return facts == null ? List.of() : facts;
    }
}
