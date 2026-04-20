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
import com.ulticode.modules.problem.dto.ProblemDetailResponse;
import com.ulticode.modules.problem.dto.ProblemDetailResponse.CompanyInfo;
import com.ulticode.modules.problem.dto.ProblemDetailResponse.DetailData;
import com.ulticode.modules.problem.dto.ProblemDetailResponse.ExampleData;
import com.ulticode.modules.problem.dto.ProblemDetailResponse.InputData;
import com.ulticode.modules.problem.dto.ProblemDetailResponse.LanguageData;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemDetail;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.ProblemLanguage;
import com.ulticode.modules.problem.mapper.ProblemDetailMapper;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.ProblemLanguageMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.service.ProblemService;
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
        int currentPageSize = (query.getPageSize() != null && query.getPageSize() > 0) ? query.getPageSize() : 20;

        // Limit page size to prevent large queries
        currentPageSize = Math.min(currentPageSize, 100);

        // Build query wrapper
        LambdaQueryWrapper<Problem> queryWrapper = new LambdaQueryWrapper<>();

        // Filter by published status (null means show all - for admin)
        if (query.getIsPublished() != null) {
            queryWrapper.eq(Problem::getIsPublished, query.getIsPublished());
        }

        // Note: Soft delete is handled by @TableLogic, but admin may want to see deleted items
        // For now, we don't explicitly filter deleted items

        // Filter by difficulty
        if (query.getDifficulty() != null && !query.getDifficulty().isBlank()) {
            queryWrapper.eq(Problem::getDifficulty, query.getDifficulty());
        }

        // Filter by status
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            queryWrapper.eq(Problem::getStatus, query.getStatus());
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

        // Order by ID ascending
        queryWrapper.orderByAsc(Problem::getId);

        // Execute paginated query
        Page<Problem> problemPage = new Page<>(currentPage, currentPageSize);
        Page<Problem> result = problemMapper.selectPage(problemPage, queryWrapper);

        // Batch-fetch all tags for the page (eliminates N+1 tag queries)
        List<Long> problemIds = result.getRecords().stream()
                .map(Problem::getId)
                .collect(Collectors.toList());
        Map<Long, List<ProblemVO.ProblemTagVO>> tagMap = batchFetchTags(problemIds);

        // Convert to VO
        List<ProblemVO> problemVOList = result.getRecords().stream()
                .map(p -> toVO(p, tagMap))
                .collect(Collectors.toList());

        return PageResult.of(problemVOList, result.getTotal(), currentPage, currentPageSize);
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
    public ProblemDetailResponse getProblemDetailResponse(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        return buildDetailResponse(problem);
    }

    @Override
    public ProblemDetailResponse getProblemDetailResponseBySlug(String slug) {
        Problem problem = findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        return buildDetailResponse(problem);
    }

    private ProblemDetailResponse buildDetailResponse(Problem problem) {
        ProblemDetailResponse response = new ProblemDetailResponse();

        // Copy basic properties from problem entity
        response.setId(problem.getId());
        response.setSlug(problem.getSlug());
        response.setTitle(problem.getTitle());
        response.setDifficulty(problem.getDifficulty() != null ? problem.getDifficulty().toUpperCase() : null);
        response.setAcceptanceRate(problem.getAcceptanceRate());
        response.setStatus(problem.getStatus());
        response.setIsPremium(problem.getIsPremium());
        response.setHasSolution(problem.getHasSolution());
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
        response.setCreatedAt(problem.getCreatedAt());
        response.setUpdatedAt(problem.getUpdatedAt());
        response.setSubmissionCount(0L);
        response.setSolutionCount(0L);
        response.setTags(Collections.emptyList());

        // Fetch and set detail data
        DetailData detailData = buildDetailData(problem.getId());
        if (detailData != null) {
            response.setDetail(detailData);
        }

        // Fetch and set examples
        List<ExampleData> examples = buildExamples(problem.getId());
        if (!examples.isEmpty()) {
            response.setExamples(examples);
        }

        // Fetch and set languages
        List<LanguageData> languages = buildLanguages(problem.getId());
        if (!languages.isEmpty()) {
            response.setLanguages(languages);
        }

        return response;
    }

    private DetailData buildDetailData(Long problemId) {
        LambdaQueryWrapper<ProblemDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemDetail::getProblemId, problemId);
        ProblemDetail detail = problemDetailMapper.selectOne(wrapper);

        if (detail == null) {
            return null;
        }

        DetailData data = new DetailData();
        data.setSummary(detail.getSummary());
        data.setConstraintsJson(parseJsonArray(detail.getConstraintsJson()));
        data.setHints(parseJsonArray(detail.getHints()));
        data.setFollowUp(detail.getFollowUp());

        // Parse companies JSON
        if (detail.getCompanies() != null && !detail.getCompanies().isBlank()) {
            try {
                List<CompanyInfo> companies = objectMapper.readValue(
                        detail.getCompanies(),
                        new TypeReference<List<CompanyInfo>>() {}
                );
                data.setCompanies(companies);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse companies JSON for problem {}", problemId, e);
            }
        }

        return data;
    }

    private List<ExampleData> buildExamples(Long problemId) {
        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(problemId);
        if (examples == null || examples.isEmpty()) {
            return Collections.emptyList();
        }

        return examples.stream().map(ex -> {
            ExampleData data = new ExampleData();
            data.setId(ex.getId());
            data.setInputText(ex.getInputText());
            data.setOutputText(ex.getOutputText());
            data.setExplanation(ex.getExplanation());

            // Parse structured inputs if present
            if (ex.getInputs() != null && !ex.getInputs().isBlank()) {
                try {
                    List<InputData> inputs = objectMapper.readValue(
                            ex.getInputs(),
                            new TypeReference<List<InputData>>() {}
                    );
                    data.setInputs(inputs);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse inputs JSON for example {}", ex.getId(), e);
                }
            }

            return data;
        }).collect(Collectors.toList());
    }

    private List<LanguageData> buildLanguages(Long problemId) {
        List<ProblemLanguage> languages = problemLanguageMapper.findByProblemId(problemId);
        if (languages == null || languages.isEmpty()) {
            return Collections.emptyList();
        }

        return languages.stream().map(lang -> {
            LanguageData data = new LanguageData();
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

        log.info("Problem created: {} by user {}", problem.getId(), SecurityUtil.getCurrentUserId());
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

        log.info("Problem updated: {} by user {}", id, SecurityUtil.getCurrentUserId());
        return toVO(problem);
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

        // Set default values for new fields not yet populated
        vo.setSubmissionCount(0L);
        vo.setSolutionCount(0L);
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
                        tagVO.setId(dto.tagName());
                        tagVO.setLabel(dto.tagName());
                        return tagVO;
                    }, Collectors.toList())
                ));
    }
}
