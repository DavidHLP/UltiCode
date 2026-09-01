package com.ulticode.modules.admin.projection;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.admin.error.AdminReadContract;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.admin.dto.AdminSubmissionVO;
import com.ulticode.modules.admin.dto.LanguageOption;
import com.ulticode.modules.admin.dto.StatusOption;
import com.ulticode.modules.admin.dto.SubmissionStatistics;
import com.ulticode.submission.api.dto.SubmissionAdminQueryDTO;
import com.ulticode.submission.api.dto.SubmissionAdminRowDTO;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link AdminSubmissionProjection}. Owns every
 * entity-to-VO projection rule and read-side aggregation for the admin
 * submission surface — see the interface javadoc for why this is a deep
 * module.
 *
 * <p>All methods are pure reads; none mutate submission state. Batch-loads
 * cross-module enrichment (user + problem) via the public
 * {@link SubmissionAdminReadPort} / {@link ProblemAdminReadPort} contracts
 * to keep the paginated list read N+1-safe (WR-05). The submission seam is
 * entity-free: {@code SubmissionAdminRowDTO} replaces the former
 * {@code Submission} entity import, and problem display data comes from
 * {@link ProblemAdminReadPort} rather than the App problem mapper.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAdminSubmissionProjection implements AdminSubmissionProjection {

    private final SubmissionAdminReadPort submissionReadPort;
    private final AdminUserEnricher userEnricher;
    private final ProblemAdminReadPort problemReadPort;
    private final Clock clock;

    // ------------------------------------------------------------------
    // Paginated list read (query build + batch enrichment)
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminSubmissionVO> getSubmissions(SubmissionAdminQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);

        PageResult<SubmissionAdminRowDTO> result;
        try {
            result = submissionReadPort.search(query, pageRequest.page(), pageRequest.pageSize());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("Submission", exception);
        }
        requirePage(result, "Submission");

        Map<String, AdminUserSummary> userMap = new HashMap<>();
        Map<Long, ProblemAdminRowDTO> problemMap = new HashMap<>();
        DegradationStatus status = result.getDegradationStatus() == null
                ? DegradationStatus.OK : result.getDegradationStatus();

        if (!result.getItems().isEmpty()) {
            Set<String> userIds = result.getItems().stream()
                    .map(SubmissionAdminRowDTO::userId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<Long> problemIds = result.getItems().stream()
                    .map(SubmissionAdminRowDTO::problemId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());

            if (!userIds.isEmpty()) {
                AdminUserEnricher.EnrichedUsers users = loadUsers(userIds);
                userMap.putAll(users.users());
                status = mergeStatus(status, users.status());
            }
            if (!problemIds.isEmpty()) {
                ProblemBatch problems = loadProblems(problemIds);
                problemMap.putAll(problems.rows());
                status = mergeStatus(status, problems.status());
            }
        }

        List<AdminSubmissionVO> vos = result.getItems().stream()
                .map(s -> toAdminVO(s, userMap, problemMap))
                .collect(Collectors.toList());

        return PageResult.of(vos, result.getTotal(), pageRequest, status);
    }

    // ------------------------------------------------------------------
    // Single-item detail read
    // ------------------------------------------------------------------

    @Override
    public AdminSubmissionVO getSubmission(String id) {
        SubmissionAdminRowDTO submission;
        try {
            submission = submissionReadPort.findById(id);
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("Submission", exception);
        }
        if (submission == null) {
            throw new BusinessException(AdminErrorCode.SUBMISSION_NOT_FOUND);
        }
        return toAdminVOWithDetails(submission);
    }

    // ------------------------------------------------------------------
    // Dashboard statistics aggregation
    // ------------------------------------------------------------------

    @Override
    public SubmissionStatistics getStatistics() {
        try {
            SubmissionStatistics stats = new SubmissionStatistics();
            stats.setTotal(submissionReadPort.countAll());

            List<com.ulticode.submission.api.dto.StatusCountDTO> statusRows =
                    submissionReadPort.countByStatus();
            List<SubmissionStatistics.StatusCount> byStatus = new ArrayList<>();
            if (statusRows == null) {
                throw AdminReadContract.ownerUnavailable("Submission");
            }
            for (com.ulticode.submission.api.dto.StatusCountDTO row : statusRows) {
                if (row == null) {
                    throw AdminReadContract.ownerUnavailable("Submission");
                }
                SubmissionStatistics.StatusCount sc = new SubmissionStatistics.StatusCount();
                sc.setStatus(row.getStatus());
                sc.setCount(row.getCount());
                byStatus.add(sc);
            }
            stats.setByStatus(byStatus);

            List<com.ulticode.submission.api.dto.LanguageCountDTO> languageRows =
                    submissionReadPort.countByLanguage();
            List<SubmissionStatistics.LanguageCount> byLanguage = new ArrayList<>();
            if (languageRows == null) {
                throw AdminReadContract.ownerUnavailable("Submission");
            }
            for (com.ulticode.submission.api.dto.LanguageCountDTO row : languageRows) {
                if (row == null) {
                    throw AdminReadContract.ownerUnavailable("Submission");
                }
                SubmissionStatistics.LanguageCount lc = new SubmissionStatistics.LanguageCount();
                lc.setLanguage(row.getLanguage());
                lc.setCount(row.getCount());
                byLanguage.add(lc);
            }
            stats.setByLanguage(byLanguage);

            LocalDateTime yesterday = LocalDateTime.now(clock).minusHours(24);
            stats.setLast24h(submissionReadPort.countCreatedSince(yesterday));
            stats.setPending(submissionReadPort.countByStatus("Pending"));
            return stats;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("Submission", exception);
        }
    }

    // ------------------------------------------------------------------
    // Filter-option derivation (keeps dropdown in sync with the enum)
    // ------------------------------------------------------------------

    @Override
    public List<StatusOption> getStatuses() {
        List<StatusOption> options = new ArrayList<>(SubmissionStatus.values().length);
        for (SubmissionStatus status : SubmissionStatus.values()) {
            StatusOption option = new StatusOption();
            option.setKey(status.getDisplayName());
            option.setLabel(status.getDisplayName());
            option.setCategory(status.getCategory());
            option.setCode(status.name());
            options.add(option);
        }
        return options;
    }

    @Override
    public List<LanguageOption> getLanguages() {
        List<String> languages;
        try {
            languages = submissionReadPort.findDistinctLanguages();
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("Submission", exception);
        }
        if (languages == null || languages.stream().anyMatch(java.util.Objects::isNull)) {
            throw AdminReadContract.ownerUnavailable("Submission");
        }
        return languages.stream()
            .map(code -> {
                LanguageOption opt = new LanguageOption();
                opt.setKey(code);
                opt.setLabel(humanizeLanguage(code));
                return opt;
            })
            .collect(Collectors.toList());
    }


    /**
     * Convert a language code stored in the database to a human-readable
     * display label. Falls back to title-cased code for unknown languages.
     *
     * @param code DB-stored language code (e.g. {@code "cpp"})
     * @return display label (e.g. {@code "C++"})
     */
    private String humanizeLanguage(String code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case "cpp" -> "C++";
            case "c" -> "C";
            case "csharp" -> "C#";
            case "java" -> "Java";
            case "python" -> "Python";
            case "javascript" -> "JavaScript";
            case "typescript" -> "TypeScript";
            case "go" -> "Go";
            case "rust" -> "Rust";
            case "ruby" -> "Ruby";
            case "kotlin" -> "Kotlin";
            case "swift" -> "Swift";
            default -> code.substring(0, 1).toUpperCase() + code.substring(1);
        };
    }

    // ------------------------------------------------------------------
    // Projection helpers (row &rarr; AdminSubmissionVO)
    // ------------------------------------------------------------------

    /**
     * Convert a submission row to a list-view AdminSubmissionVO using
     * pre-loaded batch maps (avoids N+1 on the paginated read path).
     */
    private AdminSubmissionVO toAdminVO(SubmissionAdminRowDTO submission, Map<String, AdminUserSummary> userMap, Map<Long, ProblemAdminRowDTO> problemMap) {
        if (submission == null) {
            return null;
        }

        AdminSubmissionVO vo = new AdminSubmissionVO();
        vo.setId(submission.id());
        vo.setProblemId(submission.problemId());
        vo.setUserId(submission.userId());
        vo.setLanguage(submission.language());
        vo.setStatus(submission.status());
        vo.setRuntime(submission.runtime());
        vo.setMemory(submission.memory());
        vo.setCreatedAt(submission.createdAt());
        vo.setCodeLength(submission.codeLength());

        AdminUserSummary user = userMap.get(submission.userId());
        if (user != null) {
            vo.setUsername(user.username());
        }

        ProblemAdminRowDTO problem = problemMap.get(submission.problemId());
        if (problem != null) {
            vo.setProblemTitle(problem.title());
            vo.setProblemSlug(problem.slug());
        }

        return vo;
    }

    /**
     * Convert a submission row to a detail-view AdminSubmissionVO (single
     * fetch path — enriches user + problem inline since the volume is 1).
     */
    private AdminSubmissionVO toAdminVOWithDetails(SubmissionAdminRowDTO submission) {
        // Build the list-view shape first, then layer detail fields on top.
        AdminSubmissionVO vo = toAdminVOInline(submission);
        if (vo != null) {
            vo.setCode(submission.code());
            vo.setNotes(submission.notes());
            vo.setRuntimePercentile(submission.runtimePercentile());
            vo.setMemoryPercentile(submission.memoryPercentile());
            vo.setTestDetails(submission.testDetails());
            vo.setMemoryDistBinsMb(submission.memoryDistBinsMb());
            vo.setRuntimeDistBinsMs(submission.runtimeDistBinsMs());
        }
        return vo;
    }

    /**
     * Convert a submission row to a list-view AdminSubmissionVO using
     * inline (single-row) user + problem fetches. Used only by the detail
     * read path where the row volume is 1.
     */
    private AdminSubmissionVO toAdminVOInline(SubmissionAdminRowDTO submission) {
        if (submission == null) {
            return null;
        }

        AdminSubmissionVO vo = new AdminSubmissionVO();
        vo.setId(submission.id());
        vo.setProblemId(submission.problemId());
        vo.setUserId(submission.userId());
        vo.setLanguage(submission.language());
        vo.setStatus(submission.status());
        vo.setRuntime(submission.runtime());
        vo.setMemory(submission.memory());
        vo.setCreatedAt(submission.createdAt());
        vo.setCodeLength(submission.codeLength());

        // Fetch user info (single-row detail path — no batch needed)
        AdminUserSummary user;
        try {
            user = userEnricher.enrichOne(submission.userId());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("Auth/App user", exception);
        }
        if (user != null) {
            vo.setUsername(user.username());
        }

        // Fetch problem info (single-row detail path)
        ProblemAdminRowDTO problem;
        try {
            problem = problemReadPort.findProblem(submission.problemId());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("App problem", exception);
        }
        if (problem != null) {
            vo.setProblemTitle(problem.title());
            vo.setProblemSlug(problem.slug());
        }

        return vo;
    }
    private AdminUserEnricher.EnrichedUsers loadUsers(Set<String> userIds) {
        if (userIds.isEmpty()) {
            return new AdminUserEnricher.EnrichedUsers(Collections.emptyMap(), DegradationStatus.OK);
        }
        AdminUserEnricher.EnrichedUsers result;
        try {
            result = userEnricher.enrichWithStatus(userIds);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("Auth/App user", exception);
        }
        if (result == null || result.status() == null
                || result.status() == DegradationStatus.UNAVAILABLE) {
            throw AdminReadContract.ownerUnavailable("Auth/App user");
        }
        return result;
    }

    private ProblemBatch loadProblems(Set<Long> problemIds) {
        List<ProblemAdminRowDTO> rows;
        try {
            rows = problemReadPort.findProblemsByIds(problemIds);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("App problem", exception);
        }
        if (rows == null) {
            throw AdminReadContract.ownerUnavailable("App problem");
        }
        Map<Long, ProblemAdminRowDTO> rowMap = rows.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(ProblemAdminRowDTO::id, p -> p, (first, ignored) -> first));
        DegradationStatus status = rowMap.keySet().containsAll(problemIds)
                ? DegradationStatus.OK : DegradationStatus.PARTIAL;
        return new ProblemBatch(rowMap, status);
    }

    private static DegradationStatus mergeStatus(
            DegradationStatus current, DegradationStatus next) {
        if (current == DegradationStatus.UNAVAILABLE || next == DegradationStatus.UNAVAILABLE) {
            throw AdminReadContract.ownerUnavailable("Admin read");
        }
        if (current == DegradationStatus.PARTIAL || next == DegradationStatus.PARTIAL) {
            return DegradationStatus.PARTIAL;
        }
        return current == null ? DegradationStatus.OK : current;
    }

    private static void requirePage(
            PageResult<SubmissionAdminRowDTO> page, String owner) {
        if (page == null || page.getItems() == null
                || page.getItems().stream().anyMatch(java.util.Objects::isNull)
                || page.getTotal() == null
                || page.getTotal() < 0
                || page.getDegradationStatus() == DegradationStatus.UNAVAILABLE) {
            throw AdminReadContract.ownerUnavailable(owner);
        }
    }

    private record ProblemBatch(
            Map<Long, ProblemAdminRowDTO> rows, DegradationStatus status) {
    }

    }
