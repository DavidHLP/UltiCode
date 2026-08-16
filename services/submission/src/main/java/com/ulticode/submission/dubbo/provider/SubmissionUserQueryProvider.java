package com.ulticode.submission.dubbo.provider;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.dto.LearningProgressDTO;
import com.ulticode.app.api.dto.PerformanceStats;
import com.ulticode.app.api.dto.SubmissionDetailVO;
import com.ulticode.app.api.dto.SubmissionHistoryDTO;
import com.ulticode.app.api.dto.SubmissionQueryDTO;
import com.ulticode.app.api.dto.SubmissionStatusMeta;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.app.api.service.SubmissionUserQueryPort;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
 * <p>The App provider (group=backend-app) remains the active controller
 * route until the read-routing cutover; this provider is the capability,
 * not the switch.
 */
@DubboService(group = "backend-submission", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionUserQueryProvider implements SubmissionUserQueryPort {

    private final SubmissionProjection submissionProjection;
    private final SubmissionMapper submissionMapper;
    private final SubmissionPerformanceStats performanceStats;
    private final ProblemFactsPort problemFactsPort;

    @Override
    public List<String> aggregateDates(String userId, Integer year) {
        return submissionProjection.aggregateDates(userId, year);
    }

    @Override
    public LearningProgressDTO aggregateLearningProgress(String userId) {
        return submissionProjection.aggregateLearningProgress(userId);
    }

    @Override
    public SubmissionHistoryDTO aggregateHistory(String userId) {
        return submissionProjection.aggregateHistory(userId);
    }

    @Override
    public List<SubmissionStatusMeta> getStatusCatalog() {
        return submissionProjection.getStatusCatalog();
    }

    @Override
    public SubmissionDetailVO findById(String id, String userId) {
        if (id == null) {
            return null;
        }
        Submission submission = submissionMapper.selectById(id);
        if (submission == null || userId == null || !userId.equals(submission.getUserId())) {
            return null;
        }

        PerformanceStats stats = PerformanceStats.EMPTY;
        if ("Accepted".equals(submission.getStatus())) {
            stats = performanceStats.compute(submission,
                    submission.getRuntime() != null ? submission.getRuntime() : 0,
                    submission.getMemory());
        }
        return submissionProjection.toDetailVO(submission, stats);
    }

    @Override
    public PageResult<SubmissionVO> findByUserId(String userId, SubmissionQueryDTO query) {
        int page = query != null && query.getPage() != null ? query.getPage() : 1;
        int pageSize = query != null && query.getPageSize() != null ? query.getPageSize() : 10;

        Page<Submission> pageParam = new Page<>(page, pageSize);
        IPage<Submission> result = submissionMapper.findByUserId(userId, pageParam);

        List<Submission> records = result.getRecords();
        Map<Long, ProblemFactsPort.ProblemDisplayFacts> batchFacts =
                problemFactsPort.findDisplayFactsBatch(records.stream()
                        .map(Submission::getProblemId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet()));

        List<SubmissionVO> voList = records.stream()
                .map(s -> submissionProjection.toVO(s, batchFacts))
                .toList();

        return PageResult.of(voList, result.getTotal(), page, pageSize);
    }

    @Override
    public SubmissionVO findBest(Long problemId, String userId) {
        if (problemId == null || userId == null) {
            return null;
        }
        return submissionMapper.findBestByProblemIdAndUserId(problemId, userId)
                .map(submissionProjection::toVO)
                .orElse(null);
    }
}
