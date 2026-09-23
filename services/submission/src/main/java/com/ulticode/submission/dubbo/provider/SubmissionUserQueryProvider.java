package com.ulticode.submission.dubbo.provider;

import com.ulticode.submission.api.dto.LearningProgressDTO;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.dto.SubmissionHistoryDTO;
import com.ulticode.submission.api.dto.SubmissionListItemVO;
import com.ulticode.submission.api.dto.SubmissionQueryDTO;
import com.ulticode.submission.api.dto.SubmissionStatusMeta;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionUserQueryPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.submission.read.SubmissionReadAssembly;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * Dubbo provider for user-facing Submission read aggregations and the
 * per-user detail/list/best reads exported by {@code backend-submission}.
 *
 * <p>SPLIT-004 slice-7 added the aggregations (calendar dates, learning
 * progress, submission history, status catalog). slice-8 adds the user
 * detail/list/best reads so the App {@code SubmissionController} read
 * endpoints can route through the Submission owner. All reads run locally
 * against the Submission owner schema; problem display facts are enriched
 * through the {@link com.ulticode.app.api.service.ProblemFactsPort} batch
 * seam — never a cross-owner JOIN (DEC-011).
 *
 * <p>All normal App controller reads route to this owner provider. The
 * provider owns the Submission persistence and projection path; problem display
 * facts are supplied through the App-owned batch seam.
 *
 * <p>Wire contract version {@code 1.1.0}: gates the newly added
 * {@code findByProblemId} read so 1.0.0 consumers never route to this
 * provider (an added method against an old provider binary would fail
 * method lookup). Deploy the submission service together with its
 * consumers (app-web).
 */
@DubboService(group = "backend-submission", version = "1.1.0")
@RequiredArgsConstructor
public class SubmissionUserQueryProvider implements SubmissionUserQueryPort {

    private final SubmissionReadAssembly readAssembly;

    @Override
    public List<String> aggregateDates(String userId, Integer year) {
        return readAssembly.aggregateDates(userId, year);
    }

    @Override
    public LearningProgressDTO aggregateLearningProgress(String userId) {
        return readAssembly.aggregateLearningProgress(userId);
    }

    @Override
    public SubmissionHistoryDTO aggregateHistory(String userId) {
        return readAssembly.aggregateHistory(userId);
    }

    @Override
    public List<SubmissionStatusMeta> getStatusCatalog() {
        return readAssembly.getStatusCatalog();
    }

    @Override
    public SubmissionDetailVO findById(String id, String userId) {
        return readAssembly.findById(id, userId);
    }

    @Override
    public PageResult<SubmissionVO> findByUserId(String userId, SubmissionQueryDTO query) {
        return readAssembly.findByUserId(userId, query);
    }

    @Override
    public PageResult<SubmissionListItemVO> findByProblemId(
            Long problemId, String userId, SubmissionQueryDTO query) {
        return readAssembly.findByProblemId(problemId, userId, query);
    }
    @Override
    public SubmissionVO findBest(Long problemId, String userId) {
        return readAssembly.findBest(problemId, userId);
    }
}
