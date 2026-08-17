package com.ulticode.modules.admin.service.impl;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.modules.admin.bulk.AdminBulkExecutor;
import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.problem.*;
import com.ulticode.modules.admin.dto.problem.AdminProblemMapper;
import com.ulticode.modules.admin.service.AdminProblemService;
import com.ulticode.modules.admin.service.AuditService;
import com.ulticode.modules.admin.service.ProblemCutoverService;
import com.ulticode.app.api.dto.ProblemAdminQueryDTO;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.submission.api.dto.SubmissionAdminQueryDTO;
import com.ulticode.submission.api.dto.SubmissionAdminRowDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.app.api.service.ProblemOwnerPort;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of AdminProblemService.
 * Provides tab-specific data for problem management.
 *
 * <p>ADMIN-003: every problem read (header/description/code/cases tabs,
 * flagged list, flag/moderation read-back, difficulty existence check)
 * flows through the public {@link ProblemAdminReadPort} owner contract; the
 * legacy problem mapper/entity imports are gone. Lifecycle writes
 * (publish/unpublish/delete in bulk) route through the
 * {@link ProblemCutoverService} seam; moderation/restore/difficulty writes
 * stay on {@link ProblemOwnerPort}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProblemServiceImpl implements AdminProblemService {

    private final ProblemAdminReadPort problemReadPort;
    private final AdminProblemMapper mapper;
    private final SubmissionAdminReadPort submissionReadPort;
    private final ProblemOwnerPort problemOwnerPort;
    private final ProblemCutoverService problemCutoverService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;
    private final AdminBulkExecutor bulkExecutor;

    @Override
    public PageResult<ProblemAdminVO> listProblems(ProblemAdminQueryDTO query) {
        PageResult<ProblemAdminRowDTO> result = problemReadPort.listProblems(query);
        List<ProblemAdminVO> voList = result.getItems().stream()
                .map(mapper::toAdminVO)
                .collect(Collectors.toList());
        return PageResult.of(voList, result.getTotal(), result.getPage(), result.getPageSize());
    }

    @Override
    public ProblemAdminVO getProblemById(Long id) {
        return mapper.toAdminVO(requireProblem(id));
    }

    @Override
    public HeaderDataVO getHeaderData(Long id) {
        return mapper.toHeaderDataVO(requireProblem(id));
    }

    @Override
    public DescriptionDataVO getDescriptionData(Long id) {
        var dto = problemReadPort.findDescription(id);
        if (dto == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_NOT_FOUND);
        }
        return mapper.toDescriptionDataVO(dto);
    }

    @Override
    public CodeDataVO getCodeData(Long id) {
        var dto = problemReadPort.findCode(id);
        if (dto == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_NOT_FOUND);
        }
        return mapper.toCodeDataVO(dto);
    }

    @Override
    public CasesDataVO getCasesData(Long id) {
        var dto = problemReadPort.findCases(id);
        if (dto == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_NOT_FOUND);
        }
        return mapper.toCasesDataVO(dto);
    }

    @Override
    public List<BulkProblemResultDTO> bulkAction(BulkProblemRequestDTO request) {
        AdminBulkExecutor.Run run = bulkExecutor.run(request.getIds(), request.getAction().name(), idStr -> {
            Long id = Long.parseLong(idStr);
            switch (request.getAction()) {
                case publish -> problemCutoverService.publishProblem(id);
                case unpublish -> problemCutoverService.unpublishProblem(id);
                case delete -> problemCutoverService.deleteProblem(id);
                case restore -> {
                    // P3-OWNER-001-A: route restore through the owner port.
                    int restored = problemOwnerPort.restoreDeletedByIds(List.of(id));
                    if (restored > 0) {
                        log.info("Problem id={} restored by user={}", id, currentActorId());
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
                        // ProblemOwnerPort. The existence check stays in admin
                        // because it drives the user-facing error path; the
                        // port owns the actual write.
                        if (problemReadPort.findProblem(id) != null) {
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
    public ProblemAdminVO flagProblem(Long id, String reason) {
        // P3-OWNER-001-A: foreign write goes through the owner port;
        // the read re-fetch + VO conversion stays in admin (read seam).
        requireProblem(id);
        String reportedBy = currentActorId();
        problemOwnerPort.flagProblem(id, reason, reportedBy);
        return mapper.toAdminVO(requireProblem(id));
    }

    @Override
    public ProblemAdminVO moderateProblem(Long id, String status, String notes) {
        // P3-OWNER-001-A: same pattern as flagProblem.
        requireProblem(id);
        String reviewedBy = currentActorId();
        problemOwnerPort.moderateProblem(id, status, notes, reviewedBy);
        return mapper.toAdminVO(requireProblem(id));
    }

    @Override
    public PageResult<ProblemAdminVO> getFlaggedProblems(String status, int page, int limit) {
        PageResult<ProblemAdminRowDTO> result = problemReadPort.listFlaggedProblems(status, page, limit);
        List<ProblemAdminVO> voList = result.getItems().stream()
                .map(mapper::toAdminVO)
                .collect(Collectors.toList());
        return PageResult.of(voList, result.getTotal(), page, limit);
    }

    @Override
    public List<BulkProblemResultDTO> batchModerateProblems(BatchModerateRequestDTO request) {
        List<Long> ids = request.getIds().stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());
        String reviewedBy = currentActorId();
        // P3-OWNER-001-A: bulk moderate is a foreign write; route
        // through the owner port; the App owner transaction remains authoritative.
        int affected = problemOwnerPort.moderateProblems(ids, request.getStatus(), request.getNotes(), reviewedBy);

        if (affected != ids.size()) {
            log.warn("batchModerateProblems: requested {} but only {} rows affected", ids.size(), affected);
        }

        return request.getIds().stream()
                .map(idStr -> new BulkProblemResultDTO(idStr, true, null))
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<SubmissionAdminRowDTO> getProblemSubmissions(Long id, int page, int limit) {
        requireProblem(id);
        SubmissionAdminQueryDTO query = new SubmissionAdminQueryDTO();
        query.setProblemId(id);
        query.setPage(page);
        query.setLimit(limit);
        return submissionReadPort.search(query, page, limit);
    }

    private String currentActorId() {
        String actorId = currentUserProvider.getCurrentUserId();
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED, "Authenticated admin actor is required");
        }
        return actorId;
    }

    private static final java.util.Set<String> VALID_DIFFICULTIES = java.util.Set.of("Easy", "Medium", "Hard");

    private static boolean isValidDifficulty(String value) {
        return value != null && VALID_DIFFICULTIES.contains(value);
    }

    // ========== Private Helper Methods ==========

    private ProblemAdminRowDTO requireProblem(Long id) {
        ProblemAdminRowDTO problem = problemReadPort.findProblem(id);
        if (problem == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_NOT_FOUND);
        }
        return problem;
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
