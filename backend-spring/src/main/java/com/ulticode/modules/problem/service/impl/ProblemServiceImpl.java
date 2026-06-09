package com.ulticode.modules.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.problem.dto.AdjacentProblemsVO;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemDetailAdminVO;
import com.ulticode.modules.problem.dto.ProblemDetailPublicVO;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemDetail;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.ProblemLanguage;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemDetailMapper;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.ProblemLanguageMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.problem.service.ProblemService;
import com.ulticode.modules.problem.service.ProblemVersionService;
import com.ulticode.modules.submission.service.CodeExecutionHelper;
import com.ulticode.modules.edgeoperations.service.EdgeOperationsService;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of ProblemService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemServiceImpl implements ProblemService {

    private final ProblemMapper problemMapper;
    private final ProblemDetailMapper problemDetailMapper;
    private final ProblemExampleMapper problemExampleMapper;
    private final ProblemLanguageMapper problemLanguageMapper;
    private final ObjectMapper objectMapper;
    private final ProblemVersionService problemVersionService;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final com.ulticode.modules.submission.mapper.SubmissionMapper submissionMapper;
    private final com.ulticode.modules.solution.mapper.SolutionMapper solutionMapper;
    private final EdgeOperationsService edgeOperationsService;

    @Override
    public Optional<Problem> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(problemMapper.selectById(id));
    }

    @Override
    public Optional<Problem> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<Problem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Problem::getSlug, slug);
        return Optional.ofNullable(problemMapper.selectOne(queryWrapper));
    }

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
            queryWrapper.apply("id IN (SELECT ptr.problem_id FROM problem_tag_relations ptr " +
                    "LEFT JOIN problem_tags pt ON ptr.tag_id = pt.id " +
                    "WHERE pt.label = {0} OR pt.slug = {0})", query.getTag());
        }

        // Filter by category via tag_id subquery (categories map to tag IDs)
        if (query.getCategory() != null && !query.getCategory().isBlank() && !"all".equalsIgnoreCase(query.getCategory())) {
            queryWrapper.apply("id IN (SELECT problem_id FROM problem_tag_relations WHERE tag_id = {0})", query.getCategory().toLowerCase());
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
                // Search by title
                queryWrapper.like(Problem::getTitle, searchTerm);
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

    @Override
    @Cacheable(value = "problem", key = "'getProblemById:' + #id")
    public ProblemVO getProblemById(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // Check if problem is locked (premium and user doesn't have access)
        if (Boolean.TRUE.equals(problem.getIsPremium())) {
            if (!SecurityUtil.hasRole("ADMIN") && !SecurityUtil.hasRole("SUPER_ADMIN")) {
                // Return limited info for premium problems without access
                throw new BusinessException(ErrorCode.PROBLEM_PREMIUM_REQUIRED);
            }
        }

        return toVO(problem);
    }

    @Override
    public ProblemVO getProblemBySlug(String slug) {
        Problem problem = findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // Check if problem is locked (premium and user doesn't have access)
        if (Boolean.TRUE.equals(problem.getIsPremium())) {
            if (!SecurityUtil.hasRole("ADMIN") && !SecurityUtil.hasRole("SUPER_ADMIN")) {
                throw new BusinessException(ErrorCode.PROBLEM_PREMIUM_REQUIRED);
            }
        }

        return toVO(problem);
    }

    @Override
    public ProblemDetailPublicVO getProblemDetailResponse(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        return buildPublicDetailResponse(problem);
    }

    @Override
    public ProblemDetailPublicVO getProblemDetailResponseBySlug(String slug) {
        Problem problem = findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        return buildPublicDetailResponse(problem);
    }

    @Override
    public ProblemDetailAdminVO getProblemDetailAdminResponse(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        return buildAdminDetailResponse(problem);
    }

    @Override
    public ProblemDetailAdminVO getProblemDetailAdminResponseBySlug(String slug) {
        Problem problem = findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        return buildAdminDetailResponse(problem);
    }

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
                new LambdaQueryWrapper<com.ulticode.modules.submission.entity.Submission>()
                        .eq(com.ulticode.modules.submission.entity.Submission::getProblemId, problem.getId()));
        response.setSubmissionCount(submissionCount);

        Long solutionCount = solutionMapper.selectCount(
                new LambdaQueryWrapper<com.ulticode.modules.solution.entity.Solution>()
                        .eq(com.ulticode.modules.solution.entity.Solution::getProblemId, problem.getId()));
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
            var edgeOps = edgeOperationsService.getInteractions(null, String.valueOf(problemId), EdgeOperationTargetType.PROBLEM);
            interactions.setFavorites((int) edgeOps.getFavorites());
        } catch (Exception e) {
            log.warn("Failed to query edge-operations favorites for problem {}", problemId);
            interactions.setFavorites(0);
        }

        String userId = SecurityUtil.getCurrentUserId();
        if (userId != null && detail.getInteractions() != null && !detail.getInteractions().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> interactionsMap = objectMapper.readValue(detail.getInteractions(), java.util.Map.class);
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> viewerMap = (java.util.Map<String, Object>) interactionsMap.get("viewer");
                if (viewerMap != null) {
                    Object reaction = viewerMap.get("reaction");
                    if (reaction != null) {
                        interactions.setViewerReaction(reaction.toString());
                    }
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse interactions JSON for problem {}", problemId);
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

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public ProblemVO createProblem(CreateProblemDTO createDTO) {
        // Check if slug already exists
        Optional<Problem> existingProblem = findBySlug(createDTO.getSlug());
        if (existingProblem.isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Problem with this slug already exists");
        }

        Problem problem = new Problem();
        problem.setSlug(createDTO.getSlug());
        problem.setTitle(createDTO.getTitle());
        problem.setDifficulty(createDTO.getDifficulty());
        problem.setIsPremium(createDTO.getIsPremium() != null ? createDTO.getIsPremium() : false);
        problem.setIsPublished(createDTO.getIsPublished() != null ? createDTO.getIsPublished() : true);
        problem.setStatus("todo");
        problem.setHasSolution(false);
        problem.setAcceptanceRate(BigDecimal.ZERO);
        problem.setIsFlagged(false);
        problem.setIsDeleted(false);
        problem.setVersion(1);

        // Set published info if publishing
        if (Boolean.TRUE.equals(problem.getIsPublished())) {
            problem.setPublishedAt(LocalDateTime.now());
            problem.setPublishedBy(SecurityUtil.getCurrentUserId());
        }

        problemMapper.insert(problem);

        String operatorId = SecurityUtil.getCurrentUserId();
        problemVersionService.createInitialVersion(problem.getId(), operatorId);

        log.info("Problem created: {} by user {}", problem.getId(), operatorId);
        return toVO(problem);
    }

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public ProblemVO updateProblem(Long id, UpdateProblemDTO updateDTO) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // Update fields from DTO (only non-null fields)
        if (updateDTO.getSlug() != null && !updateDTO.getSlug().equals(problem.getSlug())) {
            // Check if new slug already exists
            Optional<Problem> existingProblem = findBySlug(updateDTO.getSlug());
            if (existingProblem.isPresent() && !existingProblem.get().getId().equals(id)) {
                throw new BusinessException(ErrorCode.CONFLICT, "Problem with this slug already exists");
            }
            problem.setSlug(updateDTO.getSlug());
        }
        if (updateDTO.getTitle() != null) {
            problem.setTitle(updateDTO.getTitle());
        }
        if (updateDTO.getDifficulty() != null) {
            problem.setDifficulty(updateDTO.getDifficulty());
        }
        if (updateDTO.getIsPremium() != null) {
            problem.setIsPremium(updateDTO.getIsPremium());
        }
        if (updateDTO.getIsPublished() != null) {
            problem.setIsPublished(updateDTO.getIsPublished());
            // Set published info if publishing for the first time
            if (Boolean.TRUE.equals(updateDTO.getIsPublished()) && problem.getPublishedAt() == null) {
                problem.setPublishedAt(LocalDateTime.now());
                problem.setPublishedBy(SecurityUtil.getCurrentUserId());
            }
        }
        if (updateDTO.getHasSolution() != null) {
            problem.setHasSolution(updateDTO.getHasSolution());
        }

        problemMapper.updateById(problem);

        updateProblemDetail(id, problem, updateDTO);

        String operatorId = SecurityUtil.getCurrentUserId();
        problemVersionService.createVersion(id, "UPDATE", null, operatorId);

        log.info("Problem updated: {} by user {}", id, operatorId);
        return toVO(problem);
    }

    private void updateProblemDetail(Long problemId, Problem problem, UpdateProblemDTO updateDTO) {
        boolean hasDetailUpdate = updateDTO.getSummary() != null || updateDTO.getContent() != null
                || updateDTO.getConstraintsJson() != null || updateDTO.getHints() != null;
        if (!hasDetailUpdate && updateDTO.getLanguages() == null
                && updateDTO.getTags() == null
                && (updateDTO.getExamples() == null || updateDTO.getExamples().isBlank())) {
            return;
        }

        if (hasDetailUpdate) {
            LambdaQueryWrapper<ProblemDetail> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProblemDetail::getProblemId, problemId);
            ProblemDetail detail = problemDetailMapper.selectOne(wrapper);

            boolean isNew = false;
            if (detail == null) {
                detail = new ProblemDetail();
                detail.setProblemId(problemId);
                detail.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
                // problem_details.slug NOT NULL — denormalize from Problem on insert
                // Defensive null check: problems.slug is also NOT NULL in DB, so this
                // should never be null, but assert it to fail fast with a clear message.
                java.util.Objects.requireNonNull(problem.getSlug(),
                        "Problem.slug must not be null (DB constraint guarantees it, but assert defensively)");
                detail.setSlug(problem.getSlug());
                // constraints_json NOT NULL with no DB default — initialize to empty array
                detail.setConstraintsJson(ProblemDetail.EMPTY_JSON_ARRAY);
                isNew = true;
            }

            if (updateDTO.getSummary() != null) {
                detail.setSummary(updateDTO.getSummary());
            }
            if (updateDTO.getContent() != null) {
                detail.setContent(updateDTO.getContent());
            }
            if (updateDTO.getConstraintsJson() != null) {
                detail.setConstraintsJson(updateDTO.getConstraintsJson());
            }
            if (updateDTO.getHints() != null) {
                detail.setHints(updateDTO.getHints());
            }
            detail.setUpdatedAt(LocalDateTime.now());

            if (isNew) {
                problemDetailMapper.insert(detail);
            } else {
                problemDetailMapper.updateById(detail);
            }
        }

        if (updateDTO.getLanguages() != null) {
            updateProblemLanguages(problemId, updateDTO.getLanguages());
        }

        if (updateDTO.getExamples() != null && !updateDTO.getExamples().isBlank()) {
            try {
                List<ProblemDetailPublicVO.ExampleData> examples = objectMapper.readValue(
                        updateDTO.getExamples(),
                        new TypeReference<List<ProblemDetailPublicVO.ExampleData>>() {}
                );

                LambdaQueryWrapper<ProblemExample> deleteWrapper = new LambdaQueryWrapper<>();
                deleteWrapper.eq(ProblemExample::getProblemId, problemId);
                problemExampleMapper.delete(deleteWrapper);

                for (int i = 0; i < examples.size(); i++) {
                    ProblemDetailPublicVO.ExampleData ex = examples.get(i);
                    ProblemExample example = new ProblemExample();
                    example.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
                    example.setProblemId(problemId);
                    example.setExampleOrder(i + 1);
                    example.setInputText(ex.getInputText());
                    example.setOutputText(ex.getOutputText());
                    example.setExplanation(ex.getExplanation());
                    if (ex.getInputs() != null) {
                        example.setInputs(objectMapper.writeValueAsString(ex.getInputs()));
                    }
                    problemExampleMapper.insert(example);
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to parse examples JSON for problem {}", problemId, e);
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid examples JSON format");
            }
        }

        if (updateDTO.getTags() != null) {
            List<String> tagLabels = updateDTO.getTags();
            List<ProblemTag> existingTags = new ArrayList<>();
            for (String label : tagLabels) {
                LambdaQueryWrapper<ProblemTag> tagWrapper = new LambdaQueryWrapper<>();
                tagWrapper.eq(ProblemTag::getLabel, label);
                ProblemTag tag = problemTagMapper.selectOne(tagWrapper);
                if (tag == null) {
                    throw new BusinessException(ErrorCode.PROBLEM_TAG_NOT_FOUND, "Tag not found: " + label);
                }
                existingTags.add(tag);
            }

            LambdaQueryWrapper<ProblemTagRelation> relationWrapper = new LambdaQueryWrapper<>();
            relationWrapper.eq(ProblemTagRelation::getProblemId, problemId);
            problemTagRelationMapper.delete(relationWrapper);

            for (ProblemTag tag : existingTags) {
                ProblemTagRelation relation = new ProblemTagRelation();
                relation.setProblemId(problemId);
                relation.setTagId(tag.getId());
                problemTagRelationMapper.insert(relation);
            }
        }
    }

    private void updateProblemLanguages(Long problemId, List<com.ulticode.modules.problem.dto.LanguageConfigDTO> languages) {
        if (languages == null || languages.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<ProblemLanguage> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ProblemLanguage::getProblemId, problemId);
        problemLanguageMapper.delete(deleteWrapper);

        for (com.ulticode.modules.problem.dto.LanguageConfigDTO config : languages) {
            ProblemLanguage template = problemLanguageMapper.findByValue(config.getLanguage());
            if (template == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Unsupported language: " + config.getLanguage());
            }

            ProblemLanguage language = new ProblemLanguage();
            language.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
            language.setProblemId(problemId);
            language.setLabel(template.getLabel());
            language.setValue(template.getValue());
            language.setStyle(template.getStyle());
            language.setStarterCode(config.getStarterCode() != null ? config.getStarterCode() : template.getStarterCode());
            problemLanguageMapper.insert(language);
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public void deleteProblem(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // Soft delete is handled by MyBatis-Plus @TableLogic
        problemMapper.deleteById(id);

        log.info("Problem deleted: {} by user {}", id, SecurityUtil.getCurrentUserId());
    }

    @Override
    @Transactional
    public ProblemVO publishProblem(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        problem.setIsPublished(true);
        if (problem.getPublishedAt() == null) {
            problem.setPublishedAt(LocalDateTime.now());
            problem.setPublishedBy(SecurityUtil.getCurrentUserId());
        }

        problemMapper.updateById(problem);

        log.info("Problem published: {} by user {}", id, SecurityUtil.getCurrentUserId());
        return toVO(problem);
    }

    @Override
    @Transactional
    public ProblemVO unpublishProblem(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        problem.setIsPublished(false);

        problemMapper.updateById(problem);

        log.info("Problem unpublished: {} by user {}", id, SecurityUtil.getCurrentUserId());
        return toVO(problem);
    }

    /**
     * Overload: convert Problem to VO with pre-loaded tags.
     */
    public ProblemVO toVO(Problem problem, Map<Long, List<ProblemVO.ProblemTagVO>> tagMap) {
        return toVO(problem, tagMap, Map.of(), Map.of());
    }

    /**
     * Full overload: convert Problem to VO with pre-loaded tags and counts.
     */
    public ProblemVO toVO(Problem problem, Map<Long, List<ProblemVO.ProblemTagVO>> tagMap,
                          Map<Long, Long> submissionCounts, Map<Long, Long> solutionCounts) {
        if (problem == null) {
            return null;
        }

        ProblemVO vo = new ProblemVO();
        // Copy basic properties from entity to VO
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

    /**
     * Convert Problem to VO (no tags loaded).
     */
    @Override
    public ProblemVO toVO(Problem problem) {
        return toVO(problem, Map.of());
    }

    @Override
    public AdjacentProblemsVO getAdjacentProblems(Long id) {
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
        return ProblemVO.from(problem);
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
