package com.ulticode.submission.dubbo.provider;

import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionReadPort;
import com.ulticode.modules.submission.read.SubmissionReadAssembly;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Collection;
import java.util.List;

/**
 * Dubbo provider for {@link SubmissionReadPort} exported by
 * {@code backend-submission} so external modules (contest) project
 * submission entities to VOs from the Submission owner schema.
 *
 * <p>SPLIT-004 slice-6: user-visible projection runs in the Submission owner
 * ({@link SubmissionReadAssembly}, P0-1 hidden-case filter), then user and
 * problem summaries are enriched through the App/Auth-owned seams
 * ({@link com.ulticode.app.api.service.ProblemFactsPort}) — never reading user
 * or problem tables (DEC-011).
 * Normal App and Contest reads route to this owner provider.
 */
@DubboService(group = "backend-submission", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionReadProvider implements SubmissionReadPort {

    private final SubmissionReadAssembly readAssembly;

    @Override
    public SubmissionVO toVO(String submissionId) {
        if (submissionId == null || submissionId.isBlank()) {
            return null;
        }
        return toVOs(List.of(submissionId)).stream().findFirst().orElse(null);
    }

    @Override
    public List<SubmissionVO> toVOs(Collection<String> submissionIds) {
        return readAssembly.toVOs(submissionIds);
    }
}
