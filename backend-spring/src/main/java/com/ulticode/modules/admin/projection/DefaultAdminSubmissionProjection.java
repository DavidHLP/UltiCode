package com.ulticode.modules.admin.projection;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminSubmissionQueryDTO;
import com.ulticode.modules.admin.dto.AdminSubmissionVO;
import com.ulticode.modules.admin.dto.LanguageOption;
import com.ulticode.modules.admin.dto.StatusOption;
import com.ulticode.modules.admin.dto.SubmissionStatistics;
import com.ulticode.modules.admin.port.AdminSubmissionReadPort;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link AdminSubmissionProjection}. Owns every
 * entity-to-VO projection rule and read-side aggregation for the admin
 * submission surface &mdash; see the interface javadoc for why this is a deep
 * module.
 *
 * <p>All methods are pure reads; none mutate submission state. Batch-loads
 * cross-module enrichment (user + problem) via {@code selectBatchIds} to keep
 * the paginated list read N+1-safe (WR-05).
 *
 * <p>Cross-module entity imports ({@link User}, {@link Problem} and their
 * mappers) live here and only here &mdash; the admin submission service no
 * longer imports them after the ADR-0011 Stage 2 extraction.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAdminSubmissionProjection implements AdminSubmissionProjection {

    private final AdminSubmissionReadPort submissionReadPort;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;
    private final Clock clock;

    // ------------------------------------------------------------------
    // Paginated list read (query build + batch enrichment)
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminSubmissionVO> getSubmissions(AdminSubmissionQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);

        PageResult<Submission> result = submissionReadPort.searchSubmissions(
                query, pageRequest.page(), pageRequest.pageSize());

        // Batch-load users and problems to avoid N+1 queries (WR-05)
        Map<String, User> userMap = new HashMap<>();
        Map<Long, Problem> problemMap = new HashMap<>();
        if (!result.getItems().isEmpty()) {
            Set<String> userIds = result.getItems().stream()
                    .map(Submission::getUserId)
                    .collect(Collectors.toSet());
            Set<Long> problemIds = result.getItems().stream()
                    .map(Submission::getProblemId)
                    .collect(Collectors.toSet());

            if (!userIds.isEmpty()) {
                userMap = userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
            }
            if (!problemIds.isEmpty()) {
                problemMap = problemMapper.selectBatchIds(problemIds).stream()
                        .collect(Collectors.toMap(Problem::getId, p -> p));
            }
        }

        // Enrich with user and problem information using batch-loaded maps
        Map<String, User> finalUserMap = userMap;
        Map<Long, Problem> finalProblemMap = problemMap;
        List<AdminSubmissionVO> vos = result.getItems().stream()
                .map(s -> toAdminVO(s, finalUserMap, finalProblemMap))
                .collect(Collectors.toList());

        // All filtering now at DB level — use database total for correct pagination
        return PageResult.of(
                vos,
                result.getTotal(),
                pageRequest
        );
    }

    // ------------------------------------------------------------------
    // Single-item detail read
    // ------------------------------------------------------------------

    @Override
    public AdminSubmissionVO getSubmission(String id) {
        Submission submission = submissionReadPort.findById(id);
        if (submission == null) {
            throw new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND);
        }
        return toAdminVOWithDetails(submission);
    }

    // ------------------------------------------------------------------
    // Dashboard statistics aggregation
    // ------------------------------------------------------------------

    @Override
    public SubmissionStatistics getStatistics() {
        SubmissionStatistics stats = new SubmissionStatistics();

        // Total submissions — via the typed read port (no mapper leak)
        stats.setTotal(submissionReadPort.countAll());

        // By status — typed projection from the read port
        List<SubmissionStatistics.StatusCount> byStatus = new ArrayList<>();
        for (com.ulticode.modules.submission.dto.StatusCountDTO row : submissionReadPort.countByStatus()) {
            SubmissionStatistics.StatusCount sc = new SubmissionStatistics.StatusCount();
            sc.setStatus(row.getStatus());
            sc.setCount(row.getCount());
            byStatus.add(sc);
        }
        stats.setByStatus(byStatus);

        // By language — typed projection from the read port
        List<SubmissionStatistics.LanguageCount> byLanguage = new ArrayList<>();
        for (com.ulticode.modules.submission.dto.LanguageCountDTO row : submissionReadPort.countByLanguage()) {
            SubmissionStatistics.LanguageCount lc = new SubmissionStatistics.LanguageCount();
            lc.setLanguage(row.getLanguage());
            lc.setCount(row.getCount());
            byLanguage.add(lc);
        }
        stats.setByLanguage(byLanguage);

        // Last 24 hours
        LocalDateTime yesterday = LocalDateTime.now(clock).minusHours(24);
        stats.setLast24h(submissionReadPort.countCreatedSince(yesterday));

        // Pending count
        stats.setPending(submissionReadPort.countByStatus("Pending"));

        return stats;
    }

    // ------------------------------------------------------------------
    // Filter-option derivation (keeps dropdown in sync with the enum)
    // ------------------------------------------------------------------

    @Override
    public List<StatusOption> getStatuses() {
        // Derive filter options from the canonical enum so the dropdown
        // stays in sync with both the DB (displayName) and statistics
        // (category). Returns all 11 statuses including transient ones
        // (Judging) so admins can see and filter on every observed state.
        return Arrays.stream(SubmissionStatus.values())
            .map(s -> {
                StatusOption opt = new StatusOption();
                opt.setKey(s.getDisplayName());
                opt.setLabel(s.getDisplayName());
                opt.setCode(s.name());
                opt.setCategory(s.getCategory());
                return opt;
            })
            .collect(Collectors.toList());
    }

    @Override
    public List<LanguageOption> getLanguages() {
        return submissionReadPort.findDistinctLanguages().stream()
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
    // Projection helpers (entity &rarr; AdminSubmissionVO)
    // ------------------------------------------------------------------

    /**
     * Convert a Submission entity to a list-view AdminSubmissionVO using
     * pre-loaded batch maps (avoids N+1 on the paginated read path).
     */
    private AdminSubmissionVO toAdminVO(Submission submission, Map<String, User> userMap, Map<Long, Problem> problemMap) {
        if (submission == null) {
            return null;
        }

        AdminSubmissionVO vo = new AdminSubmissionVO();
        vo.setId(submission.getId());
        vo.setProblemId(submission.getProblemId());
        vo.setUserId(submission.getUserId());
        vo.setLanguage(submission.getLanguage());
        vo.setStatus(submission.getStatus());
        vo.setRuntime(submission.getRuntime());
        vo.setMemory(submission.getMemory());
        vo.setCreatedAt(submission.getCreatedAt());
        vo.setCodeLength(submission.getCode() != null ? submission.getCode().length() : 0);

        User user = userMap.get(submission.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
        }

        Problem problem = problemMap.get(submission.getProblemId());
        if (problem != null) {
            vo.setProblemTitle(problem.getTitle());
            vo.setProblemSlug(problem.getSlug());
        }

        return vo;
    }

    /**
     * Convert a Submission entity to a detail-view AdminSubmissionVO (single
     * fetch path — enriches user + problem inline since the volume is 1).
     */
    private AdminSubmissionVO toAdminVOWithDetails(Submission submission) {
        // Build the list-view shape first, then layer detail fields on top.
        AdminSubmissionVO vo = toAdminVOInline(submission);
        if (vo != null) {
            vo.setCode(submission.getCode());
            vo.setNotes(submission.getNotes());
            vo.setRuntimePercentile(submission.getRuntimePercentile());
            vo.setMemoryPercentile(submission.getMemoryPercentile());
            vo.setTestDetails(submission.getTestDetails());
            vo.setMemoryDistBinsMb(submission.getMemoryDistBinsMb());
            vo.setRuntimeDistBinsMs(submission.getRuntimeDistBinsMs());
        }
        return vo;
    }

    /**
     * Convert a Submission entity to a list-view AdminSubmissionVO using
     * inline (single-row) user + problem fetches. Used only by the detail
     * read path where the row volume is 1.
     */
    private AdminSubmissionVO toAdminVOInline(Submission submission) {
        if (submission == null) {
            return null;
        }

        AdminSubmissionVO vo = new AdminSubmissionVO();
        vo.setId(submission.getId());
        vo.setProblemId(submission.getProblemId());
        vo.setUserId(submission.getUserId());
        vo.setLanguage(submission.getLanguage());
        vo.setStatus(submission.getStatus());
        vo.setRuntime(submission.getRuntime());
        vo.setMemory(submission.getMemory());
        vo.setCreatedAt(submission.getCreatedAt());

        // Calculate code length
        vo.setCodeLength(submission.getCode() != null ? submission.getCode().length() : 0);

        // Fetch user info (single-row detail path — no batch needed)
        User user = userMapper.selectById(submission.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
        }

        // Fetch problem info (single-row detail path)
        Problem problem = problemMapper.selectById(submission.getProblemId());
        if (problem != null) {
            vo.setProblemTitle(problem.getTitle());
            vo.setProblemSlug(problem.getSlug());
        }

        return vo;
    }
}
