package com.ulticode.submission.dubbo.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.submission.api.dto.LanguageCountDTO;
import com.ulticode.submission.api.dto.StatusCountDTO;
import com.ulticode.submission.api.dto.SubmissionAdminQueryDTO;
import com.ulticode.submission.api.dto.SubmissionAdminRowDTO;
import com.ulticode.submission.api.dto.SubmissionDashboardChartDataDTO;
import com.ulticode.submission.api.dto.SubmissionDashboardStatsDTO;
import com.ulticode.submission.api.dto.SubmissionTestCaseDetailDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dubbo provider for {@link SubmissionAdminReadPort} exported by
 * {@code backend-submission} so backend-admin reads Submission list /
 * detail / statistics data from the Submission owner schema.
 *
 * <p>SPLIT-004 slice-5: copy of the App-owned provider with the problem-title
 * search pre-fetch routed through {@link ProblemAdminReadPort} (Dubbo to
 * {@code backend-app}), never reading problem tables (DEC-011). Pagination,
 * filter and sort semantics mirror the legacy admin adapter exactly. The App
 * provider (group=backend-app) remains the active Admin route until the
 * read-routing cutover slice; this provider is the capability, not the
 * switch.
 */
@DubboService(group = "backend-submission", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionAdminReadProvider implements SubmissionAdminReadPort {

    private final SubmissionMapper submissionMapper;
    private final ProblemAdminReadPort problemAdminReadPort;
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
        // Username search is intentionally unsupported (legacy admin TODO):
        // only submission id / language / problem-title matches are applied.
        if (StringUtils.hasText(query.getSearch())) {
            String search = query.getSearch();
            List<String> matchingUserIds = Collections.emptyList();
            List<Long> matchingProblemIds = problemAdminReadPort.searchProblemIdsByTitle(search);

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

    @Override
    public SubmissionDashboardStatsDTO loadDashboardStats(LocalDateTime now) {
        return new SubmissionDashboardStatsDTO(
                countAll(),
                countCreatedSince(now.minusDays(1)),
                countCreatedSince(now.minusWeeks(1)),
                countCreatedSince(now.minusMonths(1)),
                value(submissionMapper.calculateDashboardAcceptanceRate()));
    }

    @Override
    public List<SubmissionDashboardChartDataDTO> loadDashboardChartData(
            LocalDateTime start, LocalDateTime end, String period) {
        String dateFormat = dateFormat(period);
        return submissionMapper.countDashboardByBucket(start, end, dateFormat).stream()
                .map(row -> new SubmissionDashboardChartDataDTO(
                        (String) row.get("bucket"),
                        row.get("count") instanceof Number number ? number.longValue() : 0L))
                .toList();
    }

    private static String dateFormat(String period) {
        return switch (period == null ? "" : period.toLowerCase(Locale.ROOT)) {
            case "hour" -> "%Y-%m-%d %H:00";
            case "week" -> "%Y-%u";
            case "month" -> "%Y-%m";
            case "year" -> "%Y";
            default -> "%Y-%m-%d";
        };
    }

    private static double value(Double value) {
        return value == null ? 0.0 : value;
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
                if (value instanceof Number n) {
                    return n.intValue();
                }
            }
        }
        return null;
    }
}
