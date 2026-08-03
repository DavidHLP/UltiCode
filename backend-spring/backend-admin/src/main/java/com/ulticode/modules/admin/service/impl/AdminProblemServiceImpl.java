package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.modules.admin.bulk.AdminBulkExecutor;
import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.problem.*;
import com.ulticode.modules.admin.port.AdminProblemPort;
import com.ulticode.modules.admin.service.AdminProblemService;
import com.ulticode.modules.admin.dto.problem.AdminProblemMapper;
import com.ulticode.modules.admin.service.AuditService;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemDetail;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.ProblemLanguage;
import com.ulticode.modules.problem.mapper.ProblemDetailMapper;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.ProblemLanguageMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.app.api.service.ProblemOwnerPort;
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
    /**
     * P3-OWNER-001-A: owner-only write surface for the {@code problems}
     * row. Replaces the direct {@code problemMapper.flagProblem /
     * moderateProblem / restoreDeletedByIds / batchModerateProblems}
     * calls that lived here before the Phase 3 owner boundary was
     * established. Read paths (selectById, selectList, custom
     * queries) still go through {@code problemMapper} because the
     * admin projection / VO composition is a real read seam per
     * ADR-0011; only the WRITES are routed through the owner port.
     */
    private final ProblemOwnerPort problemOwnerPort;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;
    private final AdminBulkExecutor bulkExecutor;

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
        AdminBulkExecutor.Run run = bulkExecutor.run(request.getIds(), request.getAction().name(), idStr -> {
            Long id = Long.parseLong(idStr);
            switch (request.getAction()) {
                case publish -> problemPort.publishProblem(id);
                case unpublish -> problemPort.unpublishProblem(id);
                case delete -> problemPort.deleteProblem(id);
                case restore -> {
                    // P3-OWNER-001-A: route restore through the owner port.
                    int restored = problemOwnerPort.restoreDeletedByIds(List.of(id));
                    if (restored > 0) {
                        log.info("Problem id={} restored by user={}", id, currentUserProvider.getCurrentUserId());
                    }
                }
                case edit -> {
                    var params = request.getParams();
                    if (params != null && params.containsKey("difficulty")) {
                        String difficulty = (String) params.get("difficulty");
                        if (!isValidDifficulty(difficulty)) {
                            throw new IllegalArgumentException("Invalid difficulty value: " + difficulty);
                        }
                        // P3-OWNER-001-A: the difficulty write is a
                        // foreign-mapper UPDATE; route it through
                        // ProblemOwnerPort. The existence check
                        // (Problem != null) stays in admin because
                        // it drives the user-facing error path;
                        // the port owns the actual write.
                        Problem problem = problemMapper.selectById(id);
                        if (problem != null) {
                            problemOwnerPort.updateDifficulty(id, difficulty);
                        }
                    }
                }
            }
        }, id -> true);

        List<BulkProblemResultDTO> results = new ArrayList<>(run.items().size());
        for (AdminBulkExecutor.ItemOutcome outcome : run.items()) {
            results.add(new BulkProblemResultDTO(outcome.id(), outcome.isSuccess(), outcome.errorOrNull()));
        }
        return results;
    }

    @Override
    @Transactional
    public ProblemVO flagProblem(Long id, String reason) {
        // P3-OWNER-001-A: foreign write goes through the owner port;
        // the read re-fetch + toVO stays in admin (read seam).
        Problem problem = findProblemById(id);
        String reportedBy = currentUserProvider.getCurrentUserId();
        problemOwnerPort.flagProblem(id, reason, reportedBy);
        problem = findProblemById(id);
        return problemPort.toVO(problem);
    }

    @Override
    @Transactional
    public ProblemVO moderateProblem(Long id, String status, String notes) {
        // P3-OWNER-001-A: same pattern as flagProblem.
        Problem problem = findProblemById(id);
        String reviewedBy = currentUserProvider.getCurrentUserId();
        problemOwnerPort.moderateProblem(id, status, notes, reviewedBy);
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
        String reviewedBy = currentUserProvider.getCurrentUserId();
        // P3-OWNER-001-A: bulk moderate is a foreign write; route
        // through the owner port. The port's @Transactional joins the
        // caller's transaction so a mid-list failure rolls back.
        int affected = problemOwnerPort.moderateProblems(ids, request.getStatus(), request.getNotes(), reviewedBy);

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

    private static final java.util.Set<String> VALID_DIFFICULTIES = java.util.Set.of("Easy", "Medium", "Hard");

    private static boolean isValidDifficulty(String value) {
        return value != null && VALID_DIFFICULTIES.contains(value);
    }

    // ========== Private Helper Methods ==========

    private Problem findProblemById(Long id) {
        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_NOT_FOUND);
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
        query.setEntityType(AuditVocabulary.ENTITY_PROBLEM);
        query.setEntityId(String.valueOf(id));
        query.setPage(1);
        query.setLimit(100);
        return auditService.getAuditLogs(query).getItems();
    }
}
