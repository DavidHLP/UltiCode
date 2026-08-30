package com.ulticode.submission.dubbo.provider;

import com.ulticode.submission.api.dto.SubmissionAdjudicationFact;
import com.ulticode.submission.api.service.SubmissionAdjudicationReadPort;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Collection;
import java.util.List;

/** Exposes bounded status/generation facts for App contest finalization. */
@DubboService(group = "backend-submission", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionAdjudicationReadProvider implements SubmissionAdjudicationReadPort {

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
