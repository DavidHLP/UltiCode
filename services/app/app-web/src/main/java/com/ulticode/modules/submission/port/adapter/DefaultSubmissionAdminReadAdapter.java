package com.ulticode.modules.submission.port.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.submission.api.dto.LanguageCountDTO;
import com.ulticode.submission.api.dto.StatusCountDTO;
import com.ulticode.submission.api.dto.SubmissionAdminQueryDTO;
import com.ulticode.submission.api.dto.SubmissionAdminRowDTO;
import com.ulticode.submission.api.dto.SubmissionTestCaseDetailDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.problem.adapter.DefaultProblemAdminReadAdapter;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Owner-side adapter for {@link SubmissionAdminReadPort}.
 *
 * <p>Lives in the App submission module so the Admin BFF never imports the
 * submission entity / mapper. Every query executes inside App; the
 * paginated search preserves the legacy admin semantics exactly (problem
 * title search pre-fetch via {@link ProblemAdminReadPort}, {@code createdAt}
 * default sort, same filter set).
 *
 * <p>Detail rows ship {@code code} / {@code notes} / percentiles / test
 * details / distribution bins; list rows only carry {@code codeLength} so
 * full source code is not shipped over the RPC for paginated reads.
 */
@Component
@Primary
@RequiredArgsConstructor
public class DefaultSubmissionAdminReadAdapter implements SubmissionAdminReadPort {

    private final SubmissionMapper submissionMapper;
    private final DefaultProblemAdminReadAdapter problemAdminReadAdapter;
    private final ObjectMapper objectMapper;

    @Override
    public SubmissionAdminRowDTO findById(String id) {
        Submission submission = submissionMapper.selectById(id);
        return submission == null ? null : toDto(submission, true);
    }

    @Override
    public PageResult<SubmissionAdminRowDTO> search(SubmissionAdminQueryDTO query, int page, int pageSize) {
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();

        // Search filter — resolve at the Problem owner seam.
        // Username search is intentionally unsupported (legacy admin TODO): only
        // submission id / language / problem-title matches are applied.
        if (StringUtils.hasText(query.getSearch())) {
            String search = query.getSearch();
            List<String> matchingUserIds = Collections.emptyList();
            List<Long> matchingProblemIds = problemAdminReadAdapter.searchProblemIdsByTitle(search);

            wrapper.and(w -> {
                w.like(Submission::getId, search)
                        .or().eq(Submission::getLanguage, search);
                if (!matchingUserIds.isEmpty()) {
                    w.or().in(Submission::getUserId, matchingUserIds);
                }
                if (!matchingProblemIds.isEmpty()) {
                    w.or().in(Submission::getProblemId, matchingProblemIds);
                }
            });
        }

        if (StringUtils.hasText(query.getUserId())) {
            wrapper.eq(Submission::getUserId, query.getUserId());
        }
        if (query.getProblemId() != null) {
            wrapper.eq(Submission::getProblemId, query.getProblemId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(Submission::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getLanguage())) {
            wrapper.eq(Submission::getLanguage, query.getLanguage());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(Submission::getCreatedAt, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(Submission::getCreatedAt, query.getEndDate());
        }

        boolean isAsc = !"desc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "createdAt";
        switch (sortBy) {
            case "createdAt" -> wrapper.orderBy(true, isAsc, Submission::getCreatedAt);
            case "runtime" -> wrapper.orderBy(true, isAsc, Submission::getRuntime);
            case "memory" -> wrapper.orderBy(true, isAsc, Submission::getMemory);
            case "status" -> wrapper.orderBy(true, isAsc, Submission::getStatus);
            default -> wrapper.orderBy(true, isAsc, Submission::getCreatedAt);
        }

        Page<Submission> result = submissionMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<SubmissionAdminRowDTO> rows = result.getRecords().stream()
                .map(s -> toDto(s, false))
                .collect(Collectors.toList());
        return PageResult.of(rows, result.getTotal(), page, pageSize);
    }

    @Override
    public long countAll() {
        Long n = submissionMapper.selectCount(new QueryWrapper<>());
        return n == null ? 0L : n;
    }

    @Override
    public long countCreatedSince(LocalDateTime from) {
        Long n = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>().ge(Submission::getCreatedAt, from));
        return n == null ? 0L : n;
    }

    @Override
    public long countByStatus(String status) {
        Long n = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>().eq(Submission::getStatus, status));
        return n == null ? 0L : n;
    }

    @Override
    public List<String> findDistinctLanguages() {
        return submissionMapper.findDistinctLanguages();
    }

    @Override
    public List<StatusCountDTO> countByStatus() {
        return submissionMapper.countByStatusTyped();
    }

    @Override
    public List<LanguageCountDTO> countByLanguage() {
        return submissionMapper.countByLanguageTyped();
    }

    @Override
    public long countDistinctUsersInRange(LocalDateTime from, LocalDateTime to) {
        return submissionMapper.countDistinctUsersInRange(from, to);
    }

    @Override
    public long countSubmissionsInRange(LocalDateTime from) {
        Long n = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>().ge(Submission::getCreatedAt, from));
        return n == null ? 0L : n;
    }

    @Override
    public long countAcceptedSubmissionsInRange(LocalDateTime from) {
        Long n = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>()
                        .ge(Submission::getCreatedAt, from)
                        .eq(Submission::getStatus, "Accepted"));
        return n == null ? 0L : n;
    }

    private SubmissionAdminRowDTO toDto(Submission s, boolean detail) {
        List<Submission.TestCaseDetail> details = s.getTestDetails();
        List<SubmissionTestCaseDetailDTO> testDetails = detail && details != null
                ? details.stream().map(t -> new SubmissionTestCaseDetailDTO(
                        t.getStatus(), t.getTime(), t.getMemory(), t.getDetail(),
                        t.getOutput(), t.getExpectedOutput(),
                        t.getInputs() == null ? Collections.emptyList()
                                : t.getInputs().stream().map(in -> new SubmissionTestCaseDetailDTO.InputParam(
                                        in.getId(), in.getLabel(), in.getName(), in.getValue()))
                                        .collect(Collectors.toList()),
                        t.getCaseId(), t.getCaseScope()))
                        .collect(Collectors.toList())
                : Collections.emptyList();
        return new SubmissionAdminRowDTO(
                s.getId(), s.getProblemId(), s.getUserId(), s.getLanguage(), s.getStatus(),
                s.getRuntime(), s.getMemory(), s.getCreatedAt(),
                s.getCode() == null ? 0 : s.getCode().length(),
                detail ? s.getCode() : null,
                detail ? s.getNotes() : null,
                detail ? s.getRuntimePercentile() : null,
                detail ? s.getMemoryPercentile() : null,
                testDetails,
                detail ? normalizeBins(s.getMemoryDistBinsMb()) : null,
                detail ? normalizeBins(s.getRuntimeDistBinsMs()) : null);
    }

    private List<Integer> normalizeBins(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof List<?> list) {
            List<Integer> values = new ArrayList<>(list.size());
            for (Object item : list) {
                Integer value = extractBin(item);
                if (value != null) {
                    values.add(value);
                }
            }
            return values;
        }
        if (raw instanceof String json) {
            try {
                return normalizeBins(objectMapper.readValue(json, Object.class));
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    private static Integer extractBin(Object item) {
        if (item instanceof Number number) {
            return number.intValue();
        }
        if (item instanceof Map<?, ?> map) {
            for (String key : List.of("value", "bin", "min", "max", "count")) {
                Object value = map.get(key);
                if (value instanceof Number number) {
                    return number.intValue();
                }
            }
        }
        return null;
    }
}
