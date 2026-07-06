package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.problem.*;
import com.ulticode.modules.admin.port.AdminProblemPort;
import com.ulticode.modules.admin.service.AdminProblemService;
import com.ulticode.modules.admin.dto.problem.AdminProblemMapper;
import com.ulticode.modules.admin.service.AuditService;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.entity.*;
import com.ulticode.modules.problem.mapper.*;
import com.ulticode.modules.submission.entity.Submission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of AdminProblemService.
 * Provides tab-specific data for problem management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProblemServiceImpl implements AdminProblemService {

    private final ProblemMapper problemMapper;
    private final ProblemDetailMapper problemDetailMapper;
    private final ProblemExampleMapper problemExampleMapper;
    private final ProblemLanguageMapper problemLanguageMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final AdminProblemMapper mapper;
    private final AdminProblemPort problemPort;
    private final AuditService auditService;

    @Override
    public HeaderDataVO getHeaderData(Long id) {
        Problem problem = findProblemById(id);
        return toHeaderDataVO(problem);
    }

    @Override
    public DescriptionDataVO getDescriptionData(Long id) {
        Problem problem = findProblemById(id);
        DescriptionDataVO vo = mapper.toDescriptionDataVO(problem);

        ProblemDetail detail = findProblemDetailByProblemId(id);
        vo.setDetail(mapper.toDetailInfo(detail));
        vo.setTags(findTagsByProblemId(id));
        vo.setExamples(findExamplesByProblemId(id));

        return vo;
    }

    @Override
    public CodeDataVO getCodeData(Long id) {
        findProblemById(id);
        CodeDataVO vo = mapper.toCodeDataVO(id);

        List<ProblemLanguage> languages = problemLanguageMapper.findByProblemId(id);
        vo.setLanguages(mapper.toLanguageInfoList(languages));

        return vo;
    }

    @Override
    public CasesDataVO getCasesData(Long id) {
        findProblemById(id);
        CasesDataVO vo = mapper.toCasesDataVO(id);

        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(id);
        vo.setExamples(mapper.toExampleInfoList(examples));

        ProblemDetail detail = findProblemDetailByProblemId(id);
        vo.setDetail(mapper.toCasesDetailInfo(detail));

        vo.setTags(findTagsByProblemId(id));

        return vo;
    }

    @Override
    public List<BulkProblemResultDTO> bulkAction(BulkProblemRequestDTO request) {
        List<BulkProblemResultDTO> results = new ArrayList<>();
        for (String idStr : request.getIds()) {
            try {
                Long id = Long.parseLong(idStr);
                switch (request.getAction()) {
                    case publish -> problemPort.publishProblem(id);
                    case unpublish -> problemPort.unpublishProblem(id);
                    case delete -> problemPort.deleteProblem(id);
                    case restore -> {
                        int restored = problemMapper.restoreDeletedByIds(List.of(id));
                        if (restored > 0) {
                            log.info("Problem id={} restored by user={}", id, SecurityUtil.getCurrentUserId());
                        }
                    }
                    case edit -> {
                        var params = request.getParams();
                        if (params != null && params.containsKey("difficulty")) {
                            String difficulty = (String) params.get("difficulty");
                            if (!isValidDifficulty(difficulty)) {
                                throw new IllegalArgumentException("Invalid difficulty value: " + difficulty);
                            }
                            Problem problem = problemMapper.selectById(id);
                            if (problem != null) {
                                problem.setDifficulty(difficulty);
                                problemMapper.updateById(problem);
                            }
                        }
                    }
                }
                results.add(new BulkProblemResultDTO(idStr, true, null));
            } catch (Exception e) {
                log.error("Bulk action failed for problem id={}: {}", idStr, e.getMessage(), e);
                results.add(new BulkProblemResultDTO(idStr, false, e.getMessage()));
            }
        }
        return results;
    }

    @Override
    @Transactional
    public ProblemVO flagProblem(Long id, String reason) {
        Problem problem = findProblemById(id);
        String reportedBy = SecurityUtil.getCurrentUserId();
        problemMapper.flagProblem(id, reason, reportedBy);
        problem = findProblemById(id);
        return problemPort.toVO(problem);
    }

    @Override
    @Transactional
    public ProblemVO moderateProblem(Long id, String status, String notes) {
        Problem problem = findProblemById(id);
        String reviewedBy = SecurityUtil.getCurrentUserId();
        problemMapper.moderateProblem(id, status, notes, reviewedBy);
        problem = findProblemById(id);
        return problemPort.toVO(problem);
    }

    @Override
    public PageResult<ProblemVO> getFlaggedProblems(String status, int page, int limit) {
        int offset = (page - 1) * limit;
        List<Problem> problems = problemMapper.selectFlaggedProblems(status, limit, offset);
        long total = problemMapper.countFlaggedProblems(status);

        List<ProblemVO> voList = problems.stream()
                .map(problemPort::toVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, total, page, limit);
    }

    @Override
    @Transactional
    public List<BulkProblemResultDTO> batchModerateProblems(BatchModerateRequestDTO request) {
        List<Long> ids = request.getIds().stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());
        String reviewedBy = SecurityUtil.getCurrentUserId();
        int affected = problemMapper.batchModerateProblems(ids, request.getStatus(), request.getNotes(), reviewedBy);

        if (affected != ids.size()) {
            log.warn("batchModerateProblems: requested {} but only {} rows affected", ids.size(), affected);
        }

        return request.getIds().stream()
                .map(idStr -> new BulkProblemResultDTO(idStr, true, null))
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<Submission> getProblemSubmissions(Long id, int page, int limit) {
        findProblemById(id);
        return problemPort.findSubmissionsByProblemId(id, page, limit);
    }

    @Override
    @Transactional
    public ImportProblemsResponseDTO importProblems(ImportProblemsRequestDTO request) {
        int created = 0, updated = 0, skipped = 0, failed = 0;
        List<ImportProblemsResponseDTO.ImportResultItem> results = new ArrayList<>();

        for (ImportProblemItemDTO item : request.getProblems()) {
            try {
                Problem existing = problemPort.findBySlug(item.getSlug()).orElse(null);
                if (existing != null) {
                    switch (request.getOnConflict()) {
                        case "skip" -> {
                            skipped++;
                            results.add(new ImportProblemsResponseDTO.ImportResultItem(item.getSlug(), true, null, "skipped"));
                        }
                        case "update" -> {
                            updateFromImport(existing, item);
                            problemMapper.updateById(existing);
                            updated++;
                            results.add(new ImportProblemsResponseDTO.ImportResultItem(item.getSlug(), true, null, "updated"));
                        }
                        case "create_new" -> {
                            Problem newProblem = createFromImport(item);
                            newProblem.setSlug(item.getSlug() + "-" + System.currentTimeMillis());
                            problemMapper.insert(newProblem);
                            created++;
                            results.add(new ImportProblemsResponseDTO.ImportResultItem(item.getSlug(), true, null, "created"));
                        }
                        default -> {
                            skipped++;
                            results.add(new ImportProblemsResponseDTO.ImportResultItem(item.getSlug(), true, null, "skipped"));
                        }
                    }
                } else {
                    Problem newProblem = createFromImport(item);
                    problemMapper.insert(newProblem);
                    created++;
                    results.add(new ImportProblemsResponseDTO.ImportResultItem(item.getSlug(), true, null, "created"));
                }
            } catch (Exception e) {
                failed++;
                log.error("Import failed for problem slug={}: {}", item.getSlug(), e.getMessage(), e);
                results.add(new ImportProblemsResponseDTO.ImportResultItem(item.getSlug(), false, e.getMessage(), null));
            }
        }

        return new ImportProblemsResponseDTO(request.getProblems().size(), created, updated, skipped, failed, results);
    }

    private Problem createFromImport(ImportProblemItemDTO item) {
        Problem problem = new Problem();
        problem.setSlug(item.getSlug());
        problem.setTitle(item.getTitle());
        problem.setDifficulty(item.getDifficulty());
        problem.setStatus(item.getStatus() != null ? item.getStatus() : "todo");
        problem.setIsPremium(item.getIsPremium() != null ? item.getIsPremium() : false);
        problem.setIsPublished(item.getIsPublished() != null ? item.getIsPublished() : false);
        problem.setHasSolution(false);
        problem.setIsFlagged(false);
        problem.setIsDeleted(false);
        problem.setVersion(1);
        return problem;
    }

    private void updateFromImport(Problem existing, ImportProblemItemDTO item) {
        if (item.getTitle() != null) existing.setTitle(item.getTitle());
        if (item.getDifficulty() != null) existing.setDifficulty(item.getDifficulty());
        if (item.getStatus() != null) existing.setStatus(item.getStatus());
        if (item.getIsPremium() != null) existing.setIsPremium(item.getIsPremium());
        if (item.getIsPublished() != null) existing.setIsPublished(item.getIsPublished());
    }

    private static final java.util.Set<String> VALID_DIFFICULTIES = java.util.Set.of("Easy", "Medium", "Hard");

    private static boolean isValidDifficulty(String value) {
        return value != null && VALID_DIFFICULTIES.contains(value);
    }

    // ========== Private Helper Methods ==========

    private Problem findProblemById(Long id) {
        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }
        return problem;
    }

    private ProblemDetail findProblemDetailByProblemId(Long problemId) {
        LambdaQueryWrapper<ProblemDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemDetail::getProblemId, problemId);
        return problemDetailMapper.selectOne(wrapper);
    }

    private List<ProblemTagVO> findTagsByProblemId(Long problemId) {
        List<String> tagIds = problemTagRelationMapper.findTagIdsByProblemId(problemId);
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.toProblemTagVOList(problemTagMapper.selectBatchIds(tagIds));
    }

    private List<ProblemExampleVO> findExamplesByProblemId(Long problemId) {
        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(problemId);
        if (examples == null || examples.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.toProblemExampleVOList(examples);
    }

    private HeaderDataVO toHeaderDataVO(Problem problem) {
        return mapper.toHeaderDataVO(problem);
    }

    @Override
    public List<AuditLogVO> getProblemAuditHistory(Long id) {
        AuditLogQueryDTO query = new AuditLogQueryDTO();
        query.setEntityType(AuditActionUtil.ENTITY_PROBLEM);
        query.setEntityId(String.valueOf(id));
        query.setPage(1);
        query.setLimit(100);
        return auditService.getAuditLogs(query).getItems();
    }
}
