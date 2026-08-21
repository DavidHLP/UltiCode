package com.ulticode.submission.dubbo.provider;

import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.submission.api.service.SubmissionReadPort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dubbo provider for {@link SubmissionReadPort} exported by
 * {@code backend-submission} so external modules (contest) project
 * submission entities to VOs from the Submission owner schema.
 *
 * <p>SPLIT-004 slice-6: user-visible projection runs locally
 * ({@link SubmissionProjection}, P0-1 hidden-case filter), then user and
 * problem summaries are enriched through the App/Auth-owned seams
 * ({@link ProblemFactsPort}) — never
 * reading user or problem tables (DEC-011). The App provider
 * (group=backend-app) remains the active route until the read-routing
 * cutover slice; this provider is the capability, not the switch.
 */
@DubboService(group = "backend-submission", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionReadProvider implements SubmissionReadPort {

    private static final int BATCH_SIZE = 100;

    private final SubmissionMapper submissionMapper;
    private final SubmissionProjection submissionProjection;
    private final ProblemFactsPort problemFactsPort;

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
            List<Submission> ordered = batch.stream()
                    .map(rows::get)
                    .filter(Objects::nonNull)
                    .toList();
            Map<Long, ProblemFactsPort.ProblemDisplayFacts> facts =
                    problemFactsPort.findDisplayFactsBatch(ordered.stream()
                            .map(Submission::getProblemId)
                            .filter(Objects::nonNull)
                            .collect(java.util.stream.Collectors.toSet()));
            result.addAll(submissionProjection.toVO(ordered, facts));
        }
        return result;
    }
}
