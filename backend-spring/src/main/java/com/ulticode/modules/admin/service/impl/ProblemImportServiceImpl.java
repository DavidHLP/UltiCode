package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.util.PartialUpdate;
import com.ulticode.modules.admin.dto.problem.ImportProblemItemDTO;
import com.ulticode.modules.admin.dto.problem.ImportProblemsRequestDTO;
import com.ulticode.modules.admin.dto.problem.ImportProblemsResponseDTO;
import com.ulticode.modules.admin.port.AdminProblemPort;
import com.ulticode.modules.admin.service.ProblemImportService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deep implementation of the problem batch-import module.
 *
 * <p>Owns the whole batch outcome end-to-end behind the
 * {@link ProblemImportService} seam:
 * <ul>
 *   <li>conflict-policy resolution ({@link #conflictAction}) — skip /
 *       update / create_new, with any unknown policy folding to skip;</li>
 *   <li>per-row failure isolation — one bad row is counted as failed and
 *       logged, the rest of the batch still runs;</li>
 *   <li>create/update identity — {@link #createNew} builds the entity with
 *       the import defaults, {@link #applyPartialUpdate} carries the
 *       non-null DTO fields onto an existing row;</li>
 *   <li>slug uniqueness on conflict — {@code create_new} against an
 *       existing slug mints {@code slug + "-" + wall-clock millis};</li>
 *   <li>result accounting — created / updated / skipped / failed counters
 *       and the per-item result list.</li>
 * </ul>
 *
 * <p>Behavior is preserved exactly from the legacy inline
 * {@code AdminProblemServiceImpl#importProblems}: same conflict mapping,
 * same default-branch skip, same wall-clock slug suffix, same partial
 * fields, same exception isolation and error message capture, same
 * result-item shape. Only the structure deepens.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemImportServiceImpl implements ProblemImportService {

    private static final String CONFLICT_SKIP = "skip";
    private static final String CONFLICT_UPDATE = "update";
    private static final String CONFLICT_CREATE_NEW = "create_new";

    private static final String ACTION_CREATED = "created";
    private static final String ACTION_UPDATED = "updated";
    private static final String ACTION_SKIPPED = "skipped";

    private static final String DEFAULT_STATUS = "todo";

    private final ProblemMapper problemMapper;
    private final AdminProblemPort problemPort;

    @Override
    @Transactional
    public ImportProblemsResponseDTO importProblems(ImportProblemsRequestDTO request) {
        int created = 0, updated = 0, skipped = 0, failed = 0;
        List<ImportProblemItemDTO> items = request.getProblems();
        List<ImportProblemsResponseDTO.ImportResultItem> results = new ArrayList<>(items.size());
        String conflictPolicy = request.getOnConflict();

        for (ImportProblemItemDTO item : items) {
            ImportProblemsResponseDTO.ImportResultItem result;
            try {
                result = applyItem(conflictPolicy, item);
            } catch (Exception e) {
                log.error("Import failed for problem slug={}: {}", item.getSlug(), e.getMessage(), e);
                failed++;
                results.add(new ImportProblemsResponseDTO.ImportResultItem(item.getSlug(), false, e.getMessage(), null));
                continue;
            }
            results.add(result);
            switch (result.getAction()) {
                case ACTION_CREATED -> created++;
                case ACTION_UPDATED -> updated++;
                default -> skipped++;
            }
        }

        return new ImportProblemsResponseDTO(items.size(), created, updated, skipped, failed, results);
    }

    private ImportProblemsResponseDTO.ImportResultItem applyItem(String conflictPolicy, ImportProblemItemDTO item) {
        Optional<Problem> existing = problemPort.findBySlug(item.getSlug());
        if (existing.isEmpty()) {
            problemMapper.insert(createNew(item));
            return success(item.getSlug(), ACTION_CREATED);
        }
        return resolveConflict(conflictPolicy, existing.get(), item);
    }

    private ImportProblemsResponseDTO.ImportResultItem resolveConflict(
            String conflictPolicy, Problem existing, ImportProblemItemDTO item) {
        switch (conflictAction(conflictPolicy)) {
            case CONFLICT_UPDATE -> {
                applyPartialUpdate(existing, item);
                problemMapper.updateById(existing);
                return success(item.getSlug(), ACTION_UPDATED);
            }
            case CONFLICT_CREATE_NEW -> {
                Problem created = createNew(item);
                created.setSlug(item.getSlug() + "-" + System.currentTimeMillis());
                problemMapper.insert(created);
                return success(item.getSlug(), ACTION_CREATED);
            }
            default -> {
                return success(item.getSlug(), ACTION_SKIPPED);
            }
        }
    }

    private String conflictAction(String policy) {
        return switch (policy == null ? "" : policy) {
            case CONFLICT_UPDATE -> CONFLICT_UPDATE;
            case CONFLICT_CREATE_NEW -> CONFLICT_CREATE_NEW;
            default -> CONFLICT_SKIP;
        };
    }

    private Problem createNew(ImportProblemItemDTO item) {
        Problem problem = new Problem();
        problem.setSlug(item.getSlug());
        problem.setTitle(item.getTitle());
        problem.setDifficulty(item.getDifficulty());
        problem.setStatus(item.getStatus() != null ? item.getStatus() : DEFAULT_STATUS);
        problem.setIsPremium(item.getIsPremium() != null ? item.getIsPremium() : false);
        problem.setIsPublished(item.getIsPublished() != null ? item.getIsPublished() : false);
        problem.setHasSolution(false);
        problem.setIsFlagged(false);
        problem.setIsDeleted(false);
        problem.setVersion(1);
        return problem;
    }

    private void applyPartialUpdate(Problem existing, ImportProblemItemDTO item) {
        PartialUpdate.setIfPresentText(item, ImportProblemItemDTO::getTitle, existing::setTitle);
        PartialUpdate.setIfPresentText(item, ImportProblemItemDTO::getDifficulty, existing::setDifficulty);
        PartialUpdate.setIfPresentText(item, ImportProblemItemDTO::getStatus, existing::setStatus);
        PartialUpdate.setIfPresent(item, ImportProblemItemDTO::getIsPremium, existing::setIsPremium);
        PartialUpdate.setIfPresent(item, ImportProblemItemDTO::getIsPublished, existing::setIsPublished);
    }

    private ImportProblemsResponseDTO.ImportResultItem success(String slug, String action) {
        return new ImportProblemsResponseDTO.ImportResultItem(slug, true, null, action);
    }
}
