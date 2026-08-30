package com.ulticode.modules.submission.port.adapter;

import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionReadPort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${app.runtime.mode:dev-lite}' == 'legacy-rollback'")
@RequiredArgsConstructor
public class SubmissionReadAdapter implements SubmissionReadPort {

    private static final int BATCH_SIZE = 100;

    private final SubmissionMapper submissionMapper;
    private final SubmissionProjection submissionProjection;

    @Override
    public SubmissionVO toVO(String submissionId) {
        if (submissionId == null || submissionId.isBlank()) {
            return null;
        }
        return toVOs(List.of(submissionId)).stream().findFirst().orElse(null);
    }

    @Override
    public List<SubmissionVO> toVOs(Collection<String> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return List.of();
        }
        List<String> requested = submissionIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (requested.isEmpty()) {
            return List.of();
        }
        List<SubmissionVO> result = new ArrayList<>();
        for (int start = 0; start < requested.size(); start += BATCH_SIZE) {
            List<String> batch = requested.subList(start, Math.min(start + BATCH_SIZE, requested.size()));
            Map<String, Submission> rows = new LinkedHashMap<>();
            for (Submission row : submissionMapper.selectBatchIds(batch)) {
                if (row != null) {
                    rows.put(row.getId(), row);
                }
            }
            result.addAll(submissionProjection.toVOs(batch.stream()
                    .map(rows::get)
                    .filter(java.util.Objects::nonNull)
                    .toList()));
        }
        return result;
    }
}
