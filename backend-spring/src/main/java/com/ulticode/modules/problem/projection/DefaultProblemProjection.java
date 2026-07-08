package com.ulticode.modules.problem.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.edgeoperations.inspector.EdgeOperationInspector;
import com.ulticode.modules.problem.dto.AdjacentProblemsVO;
import com.ulticode.modules.problem.dto.ProblemDetailAdminVO;
import com.ulticode.modules.problem.dto.ProblemDetailPublicVO;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemDetail;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.ProblemLanguage;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.mapper.ProblemDetailMapper;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.ProblemLanguageMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.service.CodeExecutionHelper;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link ProblemProjection}. Owns every
 * entity-to-VO projection rule and read-side aggregation for the problem
 * domain — see the interface javadoc for why this is a deep module.
 *
 * <p>All methods are pure reads; none mutate problem state. The existence
 * checks on the detail endpoints throw {@link ErrorCode#PROBLEM_NOT_FOUND} so
 * callers receive the same 404 semantics whether they go through the public
 * or admin view.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultProblemProjection implements ProblemProjection {

    private final ProblemMapper problemMapper;
    private final ProblemDetailMapper problemDetailMapper;
    private final ProblemExampleMapper problemExampleMapper;
    private final ProblemLanguageMapper problemLanguageMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final SubmissionMapper submissionMapper;
    private final SolutionMapper solutionMapper;
    private final EdgeOperationInspector edgeOperationInspector;
    private final EdgeOperationMapper edgeOperationMapper;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;

    // ------------------------------------------------------------------
    // toVO projection
    // ------------------------------------------------------------------

    @Override
    public ProblemVO toVO(Problem problem) {
        return toVO(problem, Map.of(), Map.of(), Map.of());
    }

    @Override
    public ProblemVO toVO(Problem problem,
                           Map<Long, List<ProblemVO.ProblemTagVO>> tagMap,
                           Map<Long, Long> submissionCounts,
                           Map<Long, Long> solutionCounts) {
        if (problem == null) {
            return null;
        }

        ProblemVO vo = new ProblemVO();
        vo.setId(problem.getId());
        vo.setSlug(problem.getSlug());
        vo.setTitle(problem.getTitle());
        vo.setDifficulty(problem.getDifficulty() != null ? problem.getDifficulty().toUpperCase() : null);
        vo.setAcceptanceRate(problem.getAcceptanceRate());
        vo.setStatus(problem.getStatus());
        vo.setIsPremium(problem.getIsPremium());
        vo.setHasSolution(problem.getHasSolution());
        vo.setIsPublished(problem.getIsPublished());
        vo.setPublishedAt(problem.getPublishedAt());
        vo.setPublishedBy(problem.getPublishedBy());
        vo.setIsDeleted(problem.getIsDeleted());
        vo.setDeletedAt(problem.getDeletedAt());
        vo.setIsFlagged(problem.getIsFlagged());
        vo.setFlagReason(problem.getFlagReason());
        vo.setFlagReportedBy(problem.getFlagReportedBy());
        vo.setFlagReportedAt(problem.getFlagReportedAt());
        vo.setFlagStatus(problem.getFlagStatus());
        vo.setFlagReviewedBy(problem.getFlagReviewedBy());
        vo.setFlagReviewedAt(problem.getFlagReviewedAt());
        vo.setFlagNotes(problem.getFlagNotes());
        vo.setCreatedAt(problem.getCreatedAt());
        vo.setUpdatedAt(problem.getUpdatedAt());

        vo.setSubmissionCount(submissionCounts.getOrDefault(problem.getId(), 0L));
        vo.setSolutionCount(solutionCounts.getOrDefault(problem.getId(), 0L));
        vo.setTags(tagMap.getOrDefault(problem.getId(), List.of()));

        return vo;
    }

    // ------------------------------------------------------------------
    // Detail read models
    // ------------------------------------------------------------------

    @Override
    public ProblemDetailPublicVO publicDetailById(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        return buildPublicDetailResponse(problem);
    }

    @Override
    public ProblemDetailPublicVO publicDetailBySlug(String slug) {
        Problem problem = findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        return buildPublicDetailResponse(problem);
    }

    @Override
    public ProblemDetailAdminVO adminDetailById(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        return buildAdminDetailResponse(problem);
    }

    @Override
    public ProblemDetailAdminVO adminDetailBySlug(String slug) {
        Problem problem = findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        return buildAdminDetailResponse(problem);
    }

    // ------------------------------------------------------------------
    // List / aggregation read models
    // ------------------------------------------------------------------

    @Override
    public PageResult<ProblemVO> listProblems(ProblemQueryDTO query) {
        // Set default pagination values
        int currentPage = (query.getPage() != null && query.getPage() > 0) ? query.getPage() : 1;
        int requestedLimit = (query.getPageSize() != null && query.getPageSize() > 0) ? query.getPageSize() : 20;

        // Limit page size to prevent large queries
        int currentPageSize = Math.min(requestedLimit, 100);
        if (requestedLimit > 100) {
            log.debug("listProblems: requested limit {} capped to 100 for query (search={})",
                    requestedLimit, query.getSearch());
        }

        // Handle ARCHIVED (soft-deleted) queries via raw SQL to bypass @TableLogic
        if (query.getPublishStatus() != null && "ARCHIVED".equalsIgnoreCase(query.getPublishStatus())) {
            int offset = (currentPage - 1) * currentPageSize;
            List<Problem> deletedProblems = problemMapper.selectDeletedProblems(query.getSearch(), currentPageSize, offset);
            long total = problemMapper.countDeletedProblems(query.getSearch());

            List<Long> problemIds = deletedProblems.stream()
                    .map(Problem::getId)
                    .collect(Collectors.toList());
            Map<Long, List<ProblemVO.ProblemTagVO>> tagMap = batchFetchTags(problemIds);
            Map<Long, Long> submissionCounts = batchFetchSubmissionCounts(problemIds);
            Map<Long, Long> solutionCounts = batchFetchSolutionCounts(problemIds);

            List<ProblemVO> problemVOList = deletedProblems.stream()
                    .map(p -> toVO(p, tagMap, submissionCounts, solutionCounts))
                    .collect(Collectors.toList());

            return PageResult.of(problemVOList, total, currentPage, currentPageSize);
        }

        LambdaQueryWrapper<Problem> queryWrapper = buildProblemQueryWrapper(query);

        // Execute paginated query
        Page<Problem> problemPage = new Page<>(currentPage, currentPageSize);
        Page<Problem> result = problemMapper.selectPage(problemPage, queryWrapper);

        // Batch-fetch all tags for the page (eliminates N+1 tag queries)
        List<Long> problemIds = result.getRecords().stream()
                .map(Problem::getId)
                .collect(Collectors.toList());
        Map<Long, List<ProblemVO.ProblemTagVO>> tagMap = batchFetchTags(problemIds);
        Map<Long, Long> submissionCounts = batchFetchSubmissionCounts(problemIds);
        Map<Long, Long> solutionCounts = batchFetchSolutionCounts(problemIds);

        // Convert to VO
        List<ProblemVO> problemVOList = result.getRecords().stream()
                .map(p -> toVO(p, tagMap, submissionCounts, solutionCounts))
                .collect(Collectors.toList());

        return PageResult.of(problemVOList, result.getTotal(), currentPage, currentPageSize);
    }

    @Override
    public List<ProblemVO> listAllProblems(ProblemQueryDTO query) {
        LambdaQueryWrapper<Problem> queryWrapper = buildProblemQueryWrapper(query);
        List<Problem> problems = problemMapper.selectList(queryWrapper);
        List<Long> problemIds = problems.stream()
                .map(Problem::getId)
                .collect(Collectors.toList());
        Map<Long, List<ProblemVO.ProblemTagVO>> tagMap = batchFetchTags(problemIds);
        Map<Long, Long> submissionCounts = batchFetchSubmissionCounts(problemIds);
        Map<Long, Long> solutionCounts = batchFetchSolutionCounts(problemIds);
        return problems.stream()
                .map(p -> toVO(p, tagMap, submissionCounts, solutionCounts))
                .collect(Collectors.toList());
    }

    @Override
    public AdjacentProblemsVO adjacentProblems(Long id) {
        // D-11: validate id exists before computing adjacent; missing id used to return 200 + wrong neighbors.
        // M-2: use selectCount (LIMIT 1 implicit) instead of findById to avoid fetching the full row.
        Long count = problemMapper.selectCount(
                new LambdaQueryWrapper<Problem>().eq(Problem::getId, id));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }

        // Find the previous problem (ID less than current, ordered by ID desc, limit 1)
        LambdaQueryWrapper<Problem> prevWrapper = new LambdaQueryWrapper<>();
        prevWrapper.lt(Problem::getId, id)
                .eq(Problem::getIsPublished, true)
                .orderByDesc(Problem::getId)
                .last("LIMIT 1");
        Problem prevProblem = problemMapper.selectOne(prevWrapper);

        // Find the next problem (ID greater than current, ordered by ID asc, limit 1)
        LambdaQueryWrapper<Problem> nextWrapper = new LambdaQueryWrapper<>();
        nextWrapper.gt(Problem::getId, id)
                .eq(Problem::getIsPublished, true)
                .orderByAsc(Problem::getId)
                .last("LIMIT 1");
        Problem nextProblem = problemMapper.selectOne(nextWrapper);

        return new AdjacentProblemsVO(
                prevProblem != null ? prevProblem.getSlug() : null,
                nextProblem != null ? nextProblem.getSlug() : null
        );
    }

    @Override
    public ProblemVO findRandomPublished() {
        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<Problem>()
                .eq(Problem::getIsPublished, true)
                .last("ORDER BY RAND() LIMIT 1");
        Problem problem = problemMapper.selectOne(wrapper);
        if (problem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND, "No published problems available");
        }
        // D-12: enrich with tags + submission_count + solution_count so the random endpoint
        // returns the same data shape as list endpoint (was returning empty tags and 0 counts).
        Long id = problem.getId();
        Map<Long, List<ProblemVO.ProblemTagVO>> tagMap = batchFetchTags(List.of(id));
        Map<Long, Long> submissionCounts = batchFetchSubmissionCounts(List.of(id));
        Map<Long, Long> solutionCounts = batchFetchSolutionCounts(List.of(id));
        return toVO(problem, tagMap, submissionCounts, solutionCounts);
    }

    // ------------------------------------------------------------------
    // Internal: entity lookup
    // ------------------------------------------------------------------

    private Optional<Problem> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(problemMapper.selectById(id));
    }

    private Optional<Problem> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<Problem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Problem::getSlug, slug);
        return Optional.ofNullable(problemMapper.selectOne(queryWrapper));
    }

    // ------------------------------------------------------------------
    // Internal: detail-response assembly
    // ------------------------------------------------------------------

    private ProblemDetailPublicVO buildPublicDetailResponse(Problem problem) {
        ProblemDetailPublicVO response = new ProblemDetailPublicVO();
        populatePublicFields(response, problem);
        return response;
    }

    private ProblemDetailAdminVO buildAdminDetailResponse(Problem problem) {
        ProblemDetailAdminVO response = new ProblemDetailAdminVO();
        populatePublicFields(response, problem);

        // Admin-only fields
        response.setIsPublished(problem.getIsPublished());
        response.setPublishedAt(problem.getPublishedAt());
        response.setPublishedBy(problem.getPublishedBy());
        response.setIsDeleted(problem.getIsDeleted());
        response.setDeletedAt(problem.getDeletedAt());
        response.setIsFlagged(problem.getIsFlagged());
        response.setFlagReason(problem.getFlagReason());
        response.setFlagReportedBy(problem.getFlagReportedBy());
        response.setFlagReportedAt(problem.getFlagReportedAt());
        response.setFlagStatus(problem.getFlagStatus());
        response.setFlagReviewedBy(problem.getFlagReviewedBy());
        response.setFlagReviewedAt(problem.getFlagReviewedAt());
        response.setFlagNotes(problem.getFlagNotes());

        return response;
    }

    private void populatePublicFields(ProblemDetailPublicVO response, Problem problem) {
        response.setId(problem.getId());
        response.setSlug(problem.getSlug());
        response.setTitle(problem.getTitle());
        response.setDifficulty(problem.getDifficulty() != null ? problem.getDifficulty().toUpperCase() : null);
        response.setAcceptanceRate(problem.getAcceptanceRate());
        response.setStatus(problem.getStatus());
        response.setIsPremium(problem.getIsPremium());
        response.setHasSolution(problem.getHasSolution());
        response.setCreatedAt(problem.getCreatedAt());
        response.setUpdatedAt(problem.getUpdatedAt());

        // Real stats from database
        Long submissionCount = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getProblemId, problem.getId()));
        response.setSubmissionCount(submissionCount);

        Long solutionCount = solutionMapper.selectCount(
                new LambdaQueryWrapper<Solution>()
                        .eq(Solution::getProblemId, problem.getId()));
        response.setSolutionCount(solutionCount);

        // Tags
        List<String> tagIds = problemTagRelationMapper.findTagIdsByProblemId(problem.getId());
        if (tagIds != null && !tagIds.isEmpty()) {
            List<ProblemTag> tags = problemTagMapper.selectBatchIds(tagIds);
            List<ProblemDetailPublicVO.ProblemTagVO> tagVOs = tags.stream().map(tag -> {
                ProblemDetailPublicVO.ProblemTagVO vo = new ProblemDetailPublicVO.ProblemTagVO();
                vo.setId(tag.getId());
                vo.setLabel(tag.getLabel());
                return vo;
            }).toList();
            response.setTags(tagVOs);
        } else {
            response.setTags(Collections.emptyList());
        }

        // Fetch problem detail entity (used for both content and interactions)
        ProblemDetail problemDetail = fetchProblemDetailEntity(problem.getId());

        // Fetch and set detail data
        ProblemDetailPublicVO.DetailData detailData = buildDetailData(problemDetail);
        if (detailData != null) {
            response.setDetail(detailData);
        }

        // Set interaction counts
        if (problemDetail != null) {
            response.setInteractions(buildInteractions(problemDetail, problem.getId()));
        }

        // Fetch and set examples
        List<ProblemDetailPublicVO.ExampleData> examples = buildExamples(problem.getId());
        if (!examples.isEmpty()) {
            response.setExamples(examples);
        }

        // Fetch and set languages
        List<ProblemDetailPublicVO.LanguageData> languages = buildLanguages(problem.getId());
        if (!languages.isEmpty()) {
            response.setLanguages(languages);
        }
    }

    private ProblemDetail fetchProblemDetailEntity(Long problemId) {
        LambdaQueryWrapper<ProblemDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemDetail::getProblemId, problemId);
        return problemDetailMapper.selectOne(wrapper);
    }

    private ProblemDetailPublicVO.DetailData buildDetailData(ProblemDetail detail) {
        if (detail == null) {
            return null;
        }

        ProblemDetailPublicVO.DetailData data = new ProblemDetailPublicVO.DetailData();
        data.setSummary(detail.getSummary());
        data.setContent(detail.getContent());
        data.setConstraintsJson(parseJsonArray(detail.getConstraintsJson()));
        data.setHints(parseJsonArray(detail.getHints()));
        data.setFollowUp(detail.getFollowUp());

        // Parse companies JSON
        if (detail.getCompanies() != null && !detail.getCompanies().isBlank()) {
            try {
                List<ProblemDetailPublicVO.CompanyInfo> companies = objectMapper.readValue(
                        detail.getCompanies(),
                        new TypeReference<List<ProblemDetailPublicVO.CompanyInfo>>() {}
                );
                data.setCompanies(companies);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse companies JSON for problem {}", detail.getProblemId(), e);
            }
        }

        return data;
    }

    private ProblemDetailPublicVO.InteractionData buildInteractions(ProblemDetail detail, Long problemId) {
        ProblemDetailPublicVO.InteractionData interactions = new ProblemDetailPublicVO.InteractionData();
        interactions.setLikes(detail.getLikes() != null ? detail.getLikes() : 0);
        interactions.setDislikes(detail.getDislikes() != null ? detail.getDislikes() : 0);

        // Query edge-operations for real favorites count
        try {
            var edgeOps = edgeOperationInspector.getInteractions(null, String.valueOf(problemId), EdgeOperationTargetType.PROBLEM);
            interactions.setFavorites((int) edgeOps.getFavorites());
        } catch (Exception e) {
            log.warn("Failed to query edge-operations favorites for problem {}", problemId);
            interactions.setFavorites(0);
        }

        String userId = currentUserProvider.getCurrentUserId();
        if (userId != null) {
            // D-10: query this user's LIKE/DISLIKE/FAVORITE on this problem from edge_operations.
            // Mapper returns the most recent reaction (ORDER BY created_at DESC) so a user
            // with both LIKE and DISLIKE rows sees whichever they toggled last.
            // Replaces the previous JSON-column single-viewer hack (problem_details.interactions
            // held one viewer for everyone, so it never reflected the actual requester).
            try {
                String reaction = edgeOperationMapper.findViewerReaction(
                        userId, String.valueOf(problemId), EdgeOperationTargetType.PROBLEM.name());
                if (reaction != null) {
                    ProblemDetailPublicVO.ViewerData viewer = new ProblemDetailPublicVO.ViewerData();
                    viewer.setReaction(reaction.toLowerCase());
                    interactions.setViewer(viewer);
                }
            } catch (Exception e) {
                log.warn("Failed to query viewer reaction for problem {} user {}: {}",
                        problemId, userId, e.getMessage());
            }
        }
        return interactions;
    }

    private List<ProblemDetailPublicVO.ExampleData> buildExamples(Long problemId) {
        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(problemId);
        if (examples == null || examples.isEmpty()) {
            return Collections.emptyList();
        }

        return examples.stream().map(ex -> {
            ProblemDetailPublicVO.ExampleData data = new ProblemDetailPublicVO.ExampleData();
            data.setId(ex.getId());
            data.setInputText(ex.getInputText());
            data.setOutputText(ex.getOutputText());
            data.setExplanation(ex.getExplanation());

            // Parse structured inputs if present
            if (ex.getInputs() != null && !ex.getInputs().isBlank()) {
                try {
                    List<ProblemDetailPublicVO.InputData> inputs = objectMapper.readValue(
                            ex.getInputs(),
                            new TypeReference<List<ProblemDetailPublicVO.InputData>>() {}
                    );
                    data.setInputs(inputs);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse inputs JSON for example {}", ex.getId(), e);
                }
            }

            return data;
        }).collect(Collectors.toList());
    }

    private List<ProblemDetailPublicVO.LanguageData> buildLanguages(Long problemId) {
        List<ProblemLanguage> languages = problemLanguageMapper.findByProblemId(problemId);
        if (languages == null || languages.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> supported = CodeExecutionHelper.SUPPORTED_LANGUAGES;
        return languages.stream()
                .filter(lang -> supported.contains(lang.getValue().toLowerCase().trim()))
                .map(lang -> {
                    ProblemDetailPublicVO.LanguageData data = new ProblemDetailPublicVO.LanguageData();
                    data.setId(lang.getId());
                    data.setLabel(lang.getLabel());
                    data.setValue(lang.getValue());
                    data.setStyle(lang.getStyle());
                    data.setStarterCode(lang.getStarterCode());
                    return data;
                }).collect(Collectors.toList());
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON array: {}", json, e);
            return Collections.emptyList();
        }
    }

    // ------------------------------------------------------------------
    // Internal: list query + batch-fetch helpers
    // ------------------------------------------------------------------

    private LambdaQueryWrapper<Problem> buildProblemQueryWrapper(ProblemQueryDTO query) {
        LambdaQueryWrapper<Problem> queryWrapper = new LambdaQueryWrapper<>();

        // Filter by published status (null means show all - for admin)
        if (query.getIsPublished() != null) {
            queryWrapper.eq(Problem::getIsPublished, query.getIsPublished());
        }

        // Filter by publishStatus (DRAFT/PUBLISHED/ARCHIVED)
        if (query.getPublishStatus() != null && !query.getPublishStatus().isBlank()) {
            switch (query.getPublishStatus().toUpperCase()) {
                case "DRAFT" -> {
                    queryWrapper.eq(Problem::getIsPublished, false);
                }
                case "PUBLISHED" -> {
                    queryWrapper.eq(Problem::getIsPublished, true);
                }
                case "ARCHIVED" -> {
                    // Archived = soft-deleted; handled separately in listProblems via raw SQL
                    return queryWrapper;
                }
            }
        }

        // Note: Soft delete is handled by @TableLogic, but admin may want to see deleted items
        // For now, we don't explicitly filter deleted items

        // Filter by difficulty (case-insensitive)
        if (query.getDifficulty() != null && !query.getDifficulty().isBlank()) {
            queryWrapper.apply("UPPER(difficulty) = UPPER({0})", query.getDifficulty());
        }

        // Filter by status
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            queryWrapper.eq(Problem::getStatus, query.getStatus());
        }

        // Filter by tag via subquery
        if (query.getTag() != null && !query.getTag().isBlank()) {
            // D-09: match by id-style string (e.g. "tag-linked-list") in addition to label/slug.
            // pt.id is VARCHAR(40) so passing a string binds cleanly; the index edge_ops_target
            // (target_type, target_id) is not used here, but problem_tag_relations has
            // its own index on tag_id so the inner subquery stays at O(distinct problems).
            queryWrapper.apply("id IN (SELECT ptr.problem_id FROM problem_tag_relations ptr " +
                    "LEFT JOIN problem_tags pt ON ptr.tag_id = pt.id " +
                    "WHERE pt.label = {0} OR pt.slug = {0} OR pt.id = {0})", query.getTag());
        }

        // Filter by category: top-level categories (algorithms/database/shell/concurrency)
        // are stored as problem_tags rows whose slug follows the 'problem-category-<value>'
        // namespace (seed: V20260615140000__Seed_Problem_Category_Tags.sql). The frontend
        // sends the bare value (e.g. "algorithms"); resolve it via the namespaced slug, joined
        // through problem_tag_relations. slug is backed by unique index problem_tags_slug_key.
        if (query.getCategory() != null && !query.getCategory().isBlank() && !"all".equalsIgnoreCase(query.getCategory())) {
            queryWrapper.apply(
                    "id IN (SELECT ptr.problem_id FROM problem_tag_relations ptr " +
                    "JOIN problem_tags pt ON ptr.tag_id = pt.id " +
                    "WHERE pt.slug = CONCAT('problem-category-', {0}))",
                    query.getCategory().toLowerCase());
        }

        // Filter by premium status
        if (query.getIsPremium() != null) {
            queryWrapper.eq(Problem::getIsPremium, query.getIsPremium());
        }

        // Search by ID or title
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            String searchTerm = query.getSearch().trim();
            try {
                // Try to parse as ID
                Long id = Long.parseLong(searchTerm);
                queryWrapper.eq(Problem::getId, id);
            } catch (NumberFormatException e) {
                // D-08: Search by title OR slug. LambdaQueryWrapper#like needs SFunction so we use
                // apply() with raw SQL; chained apply + or() avoids the {0}/{1} duplicate bind.
                queryWrapper.apply("title LIKE CONCAT('%', {0}, '%')", searchTerm)
                        .or().apply("slug LIKE CONCAT('%', {0}, '%')", searchTerm);
            }
        }

        // Dynamic sorting
        String sortBy = query.getSortBy();
        String sortOrder = query.getSortOrder();
        if (sortBy != null && !sortBy.isBlank() && !"default".equals(sortBy)) {
            boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
            switch (sortBy) {
                case "title" -> queryWrapper.orderBy(true, isAsc, Problem::getTitle);
                case "difficulty" -> queryWrapper.orderBy(true, isAsc, Problem::getDifficulty);
                case "createdAt", "created_at" -> queryWrapper.orderBy(true, isAsc, Problem::getCreatedAt);
                case "updatedAt", "updated_at" -> queryWrapper.orderBy(true, isAsc, Problem::getUpdatedAt);
                default -> queryWrapper.orderByDesc(Problem::getCreatedAt);
            }
        } else {
            queryWrapper.orderByDesc(Problem::getCreatedAt);
        }

        return queryWrapper;
    }

    /**
     * Batch-fetch all tags for a list of problem IDs in a single query.
     * Groups results into a map of problemId -> list of ProblemTagVO.
     */
    private Map<Long, List<ProblemVO.ProblemTagVO>> batchFetchTags(List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return Map.of();
        }
        List<ProblemMapper.ProblemTagDTO> tagDTOs = problemMapper.selectTagsByProblemIds(problemIds);
        return tagDTOs.stream()
                .collect(Collectors.groupingBy(
                        ProblemMapper.ProblemTagDTO::problemId,
                        Collectors.mapping(dto -> {
                        ProblemVO.ProblemTagVO tagVO = new ProblemVO.ProblemTagVO();
                        tagVO.setId(dto.tagId());
                        tagVO.setLabel(dto.tagName());
                        return tagVO;
                    }, Collectors.toList())
                ));
    }

    private Map<Long, Long> batchFetchSubmissionCounts(List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return Map.of();
        }
        return problemMapper.countSubmissionsByProblemIds(problemIds)
                .stream()
                .collect(Collectors.toMap(
                        ProblemMapper.ProblemCountDTO::problemId,
                        ProblemMapper.ProblemCountDTO::count
                ));
    }

    private Map<Long, Long> batchFetchSolutionCounts(List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return Map.of();
        }
        return problemMapper.countSolutionsByProblemIds(problemIds)
                .stream()
                .collect(Collectors.toMap(
                        ProblemMapper.ProblemCountDTO::problemId,
                        ProblemMapper.ProblemCountDTO::count
                ));
    }
}
