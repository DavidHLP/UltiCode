package com.ulticode.modules.submission.read;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
import com.ulticode.submission.api.dto.PerformanceStats;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.dto.SubmissionListItemVO;
import com.ulticode.submission.api.dto.SubmissionQueryDTO;
import com.ulticode.submission.api.dto.SubmissionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deep module for Submission owner read assembly.
 *
 * <p>This module owns the mechanics shared by the exported read providers:
 * bounded storage reads, caller-order reconstruction, App-owned problem-facts
 * enrichment, empty-page short-circuiting, pagination normalization, and
 * selection of the list/detail/best projection. The providers remain thin wire
 * adapters and the write/intake path stays outside this module.
 */
@Component
@RequiredArgsConstructor
public class SubmissionReadAssembly {

    private static final int READ_BATCH_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final SubmissionMapper submissionMapper;
    private final SubmissionProjection submissionProjection;
    private final SubmissionPerformanceStats performanceStats;
    private final ProblemFactsPort problemFactsPort;

    /**
     * Read submissions by id in bounded chunks while preserving the caller's
     * first-seen order and omitting missing rows.
     */
    public List<SubmissionVO> toVOs(Collection<String> submissionIds) {
        List<String> requested = normalizeIds(submissionIds);
        if (requested.isEmpty()) {
            return List.of();
        }

        List<SubmissionVO> result = new ArrayList<>();
        for (int start = 0; start < requested.size(); start += READ_BATCH_SIZE) {
            List<String> batch = requested.subList(start,
                    Math.min(start + READ_BATCH_SIZE, requested.size()));
            List<Submission> ordered = orderRows(batch, submissionMapper.selectBatchIds(batch));
            if (ordered.isEmpty()) {
                continue;
            }
            List<SubmissionVO> projected = submissionProjection.toVO(ordered, factsFor(ordered));
            if (projected != null) {
                result.addAll(projected);
            }
        }
        return result;
    }

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
        return submissionProjection.toDetailVO(submission, stats, factsFor(List.of(submission)));
    }

    public PageResult<SubmissionVO> findByUserId(String userId, SubmissionQueryDTO query) {
        PaginationRequest pagination = pagination(query);
        IPage<Submission> result = submissionMapper.findByUserId(
                userId, new Page<>(pagination.page(), pagination.pageSize()));
        List<Submission> records = result.getRecords();
        List<SubmissionVO> items = submissionProjection.toVO(records, factsFor(records));
        return PageResult.of(items == null ? List.of() : items,
                result.getTotal(), pagination);
    }

    public PageResult<SubmissionListItemVO> findByProblemId(
            Long problemId, String userId, SubmissionQueryDTO query) {
        if (problemId == null || userId == null) {
            return PageResult.of(List.of(), 0L,
                    PaginationRequest.of(null, null, DEFAULT_PAGE_SIZE));
        }

        PaginationRequest pagination = pagination(query);
        IPage<Submission> result = submissionMapper.findByProblemId(
                problemId, userId, new Page<>(pagination.page(), pagination.pageSize()));
        List<Submission> records = result.getRecords();
        Map<Long, ProblemFactsPort.ProblemDisplayFacts> facts = factsFor(records, problemId);
        List<SubmissionListItemVO> items = records.stream()
                .map(row -> submissionProjection.toListItemVO(row, facts.get(problemId)))
                .toList();
        return PageResult.of(items, result.getTotal(), pagination);
    }

    public SubmissionVO findBest(Long problemId, String userId) {
        if (problemId == null || userId == null) {
            return null;
        }
        return submissionMapper.findBestByProblemIdAndUserId(problemId, userId)
                .map(submission -> submissionProjection.toVO(
                        submission, factsFor(List.of(submission))))
                .orElse(null);
    }

    private PaginationRequest pagination(SubmissionQueryDTO query) {
        return PaginationRequest.of(
                query != null ? query.getPage() : null,
                query != null ? query.getPageSize() : null,
                DEFAULT_PAGE_SIZE);
    }

    private Map<Long, ProblemFactsPort.ProblemDisplayFacts> factsFor(
            Collection<Submission> submissions) {
        return factsFor(submissions, null);
    }

    private Map<Long, ProblemFactsPort.ProblemDisplayFacts> factsFor(
            Collection<Submission> submissions, Long fallbackProblemId) {
        if (submissions == null || submissions.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<Long> problemIds = new LinkedHashSet<>();
        for (Submission submission : submissions) {
            if (submission != null && submission.getProblemId() != null) {
                problemIds.add(submission.getProblemId());
            }
        }
        if (problemIds.isEmpty() && fallbackProblemId != null) {
            problemIds.add(fallbackProblemId);
        }
        if (problemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProblemFactsPort.ProblemDisplayFacts> facts =
                problemFactsPort.findDisplayFactsBatch(problemIds);
        return facts == null ? Map.of() : facts;
    }

    private static List<String> normalizeIds(Collection<String> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return List.of();
        }
        return submissionIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    private static List<Submission> orderRows(
            List<String> requestedIds, List<Submission> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, Submission> rowsById = new LinkedHashMap<>();
        for (Submission row : rows) {
            if (row != null) {
                rowsById.put(row.getId(), row);
            }
        }
        return requestedIds.stream()
                .map(rowsById::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
