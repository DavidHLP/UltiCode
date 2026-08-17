package com.ulticode.modules.submission.port.adapter;

import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionReadPort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link SubmissionReadPort}, bridging the contest
 * module's {@code toVO(String submissionId)} read seam to the submission
 * module's projection layer.
 *
 * <p>Loads the entity via {@link SubmissionMapper#selectById}, then projects
 * via {@link SubmissionProjection#toVO(Submission)}. Returns {@code null}
 * when the submission is not found, matching the port contract.
 */
@Component
@RequiredArgsConstructor
public class SubmissionReadAdapter implements SubmissionReadPort {

    private final SubmissionMapper submissionMapper;
    private final SubmissionProjection submissionProjection;

    @Override
    public SubmissionVO toVO(String submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            return null;
        }
        return submissionProjection.toVO(submission);
    }
}
