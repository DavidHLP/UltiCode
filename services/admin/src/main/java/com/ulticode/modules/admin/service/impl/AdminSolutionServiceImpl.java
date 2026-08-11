package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.bulk.AdminBulkExecutor;
import com.ulticode.modules.admin.dto.AdminSolutionVO;
import com.ulticode.modules.admin.projection.AdminSolutionProjection;
import com.ulticode.modules.admin.service.AdminSolutionService;
import com.ulticode.app.api.service.SolutionOwnerPort;
import com.ulticode.app.api.service.SolutionOwnerPort.DeleteResult;
import com.ulticode.app.api.service.SolutionOwnerPort.FlagResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Write-side implementation of {@link AdminSolutionService}.
 *
 * <p>P3-OWNER-001-E: all solution write operations (flag, unflag, delete, publish,
 * unpublish) route through {@link SolutionOwnerPort} rather than directly
 * importing {@code SolutionMapper} or {@code ProblemMapper}.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSolutionServiceImpl implements AdminSolutionService {

    private final SolutionOwnerPort solutionOwnerPort;
    private final AdminSolutionProjection solutionProjection;
    private final Clock clock;
    private final AdminBulkExecutor bulkExecutor;

    @Override
    @Audited(action = AuditVocabulary.FLAG_SOLUTION, entityType = AuditVocabulary.ENTITY_SOLUTION)
    public AdminSolutionVO flagSolution(String id, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        FlagResult res = solutionOwnerPort.flagSolution(id, reason, now);

        AuditContext.setUserId(res.authorUserId());
        AuditContext.setEntityId(id);

        AuditContext.setOldValues(Map.of(
            "isFlagged", res.oldIsFlagged(),
            "flaggedReason", res.oldFlaggedReason()
        ));
        AuditContext.setNewValues(Map.of(
            "isFlagged", true,
            "flaggedReason", reason != null ? reason : ""
        ));

        return solutionProjection.getSolution(id);
    }

    @Override
    @Audited(action = AuditVocabulary.UNFLAG_SOLUTION, entityType = AuditVocabulary.ENTITY_SOLUTION)
    public AdminSolutionVO unflagSolution(String id) {
        FlagResult res = solutionOwnerPort.unflagSolution(id);

        AuditContext.setUserId(res.authorUserId());
        AuditContext.setEntityId(id);

        AuditContext.setOldValues(Map.of(
            "isFlagged", res.oldIsFlagged(),
            "flaggedReason", res.oldFlaggedReason()
        ));
        AuditContext.setNewValues(Map.of(
            "isFlagged", false,
            "flaggedReason", ""
        ));

        return solutionProjection.getSolution(id);
    }

    @Override
    @Audited(action = AuditVocabulary.DELETE_SOLUTION, entityType = AuditVocabulary.ENTITY_SOLUTION)
    public void deleteSolution(String id) {
        DeleteResult res = solutionOwnerPort.deleteSolution(id);

        AuditContext.setUserId(res.authorUserId());
        AuditContext.setEntityId(id);

        AuditContext.setOldValues(Map.of(
            "title", res.title(),
            "problemId", res.problemId()
        ));
    }

    @Override
    @Audited(action = AuditVocabulary.BULK_SOLUTION_ACTION, entityType = AuditVocabulary.ENTITY_SOLUTION, captureNewState = false)
    public List<BulkActionResult> bulkAction(List<String> ids, String action) {
        AuditContext.setEntityId(String.join(",", ids));

        Set<String> existingIds = solutionOwnerPort.findExistingIds(ids);

        AdminBulkExecutor.Run run = bulkExecutor.run(ids, action, id -> {
            switch (action) {
                case "publish" -> solutionOwnerPort.setPublished(id, true, LocalDateTime.now(clock));
                case "unpublish" -> solutionOwnerPort.setPublished(id, false, null);
                case "delete" -> deleteSolution(id);
                case "unflag" -> unflagSolution(id);
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            }
        }, existingIds::contains);

        List<BulkActionResult> results = new ArrayList<>(run.items().size());
        for (AdminBulkExecutor.ItemOutcome outcome : run.items()) {
            if (outcome instanceof AdminBulkExecutor.NotFound) {
                results.add(BulkActionResult.failure(outcome.id(), BulkActionResult.NOT_FOUND_MESSAGE));
            } else if (outcome instanceof AdminBulkExecutor.Success) {
                results.add(BulkActionResult.success(outcome.id()));
            } else {
                results.add(BulkActionResult.failure(outcome.id(), outcome.errorOrNull()));
            }
        }
        return results;
    }
}
