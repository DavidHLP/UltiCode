package com.ulticode.submission.dubbo.provider;

import com.ulticode.submission.api.dto.LearningProgressDTO;
import com.ulticode.submission.api.dto.PerformanceStats;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.dto.SubmissionHistoryDTO;
import com.ulticode.submission.api.dto.SubmissionListItemVO;
import com.ulticode.submission.api.dto.SubmissionQueryDTO;
import com.ulticode.submission.api.dto.SubmissionStatusMeta;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionUserQueryPort;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p>Normal App controller reads route to this owner provider; the App-local
 * projection is retained only for explicit legacy rollback.
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

    /** Preserved historical default page size for user-facing lists. */
    private static final int DEFAULT_PAGE_SIZE = 10;

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
        Map<Long, ProblemFactsPort.ProblemDisplayFacts> facts =
                problemFactsPort.findDisplayFactsBatch(submission.getProblemId() == null
                        ? Set.of()
                        : Set.of(submission.getProblemId()));
        return submissionProjection.toDetailVO(submission, stats, facts);
    }

    @Override
    public PageResult<SubmissionVO> findByUserId(String userId, SubmissionQueryDTO query) {
        PaginationRequest pagination = PaginationRequest.of(
                query != null ? query.getPage() : null,
                query != null ? query.getPageSize() : null,
                DEFAULT_PAGE_SIZE);

        Page<Submission> pageParam = new Page<>(pagination.page(), pagination.pageSize());
        IPage<Submission> result = submissionMapper.findByUserId(userId, pageParam);

        List<Submission> records = result.getRecords();
        // Empty pages skip the remote facts RPC: the synchronous enrichment
        // would only add latency and make an empty page fail when the App
        // facts service is degraded.
        Map<Long, ProblemFactsPort.ProblemDisplayFacts> batchFacts = records.isEmpty()
                ? Map.of()
                : problemFactsPort.findDisplayFactsBatch(records.stream()
                        .map(Submission::getProblemId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet()));

        List<SubmissionVO> voList = submissionProjection.toVO(records, batchFacts);

        return PageResult.of(voList, result.getTotal(), pagination.page(), pagination.pageSize());
    }

    @Override
    public PageResult<SubmissionListItemVO> findByProblemId(
            Long problemId, String userId, SubmissionQueryDTO query) {
        if (problemId == null || userId == null) {
            return PageResult.of(List.<SubmissionListItemVO>of(), 0L, 1, 10);
        }
        PaginationRequest pagination = PaginationRequest.of(
                query != null ? query.getPage() : null,
                query != null ? query.getPageSize() : null,
                DEFAULT_PAGE_SIZE);
        Page<Submission> pageParam = new Page<>(pagination.page(), pagination.pageSize());
        IPage<Submission> result = submissionMapper.findByProblemId(problemId, userId, pageParam);
        List<Submission> records = result.getRecords();
        // Empty pages skip the remote facts RPC: the synchronous enrichment
        // would only add latency and make an empty page fail when the App
        // facts service is degraded.
        Map<Long, ProblemFactsPort.ProblemDisplayFacts> facts = records.isEmpty()
                ? Map.of()
                : problemFactsPort.findDisplayFactsBatch(Set.of(problemId));
        List<SubmissionListItemVO> items = records.stream()
                .map(row -> submissionProjection.toListItemVO(row, facts.get(problemId)))
                .toList();
        return PageResult.of(items, result.getTotal(), pagination.page(), pagination.pageSize());
    }
    @Override
    public SubmissionVO findBest(Long problemId, String userId) {
        if (problemId == null || userId == null) {
            return null;
        }
        return submissionMapper.findBestByProblemIdAndUserId(problemId, userId)
                .map(submission -> submissionProjection.toVO(
                        submission,
                        problemFactsPort.findDisplayFactsBatch(Set.of(problemId))))
                .orElse(null);
    }
}
