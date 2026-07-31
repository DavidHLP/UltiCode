package com.ulticode.modules.admin.service.impl;

import com.ulticode.modules.admin.dto.problem.ImportProblemItemDTO;
import com.ulticode.modules.admin.dto.problem.ImportProblemsRequestDTO;
import com.ulticode.modules.admin.dto.problem.ImportProblemsResponseDTO;
import com.ulticode.modules.admin.port.AdminProblemPort;
import com.ulticode.modules.admin.service.ProblemImportService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.port.ProblemOwnerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deep implementation of the problem batch-import module.
 *
 * <p>Owns the whole batch outcome end-to-end behind the
 * {@link ProblemImportService} seam:
 * <ul>
 *   <li>conflict-policy resolution ({@link ConflictPolicy}) — skip /
 *       update / create_new, with any unknown policy folding to skip;</li>
 *   <li>per-row failure isolation — one bad row is counted as failed and
 *       logged, the rest of the batch still runs;</li>
 *   <li>create/update identity — {@link #createNew} builds the entity with
 *       the import defaults, {@link #applyPartialUpdate} carries the
 *       non-null DTO fields onto an existing row;</li>
 *   <li>slug uniqueness on conflict — {@code create_new} against an
 *       existing slug mints {@code slug + "-" + wall-clock millis};</li>
 *   <li>result accounting — {@link ImportAction} counters accumulated via
 *       an {@link EnumMap} and the per-item result list with the wire
 *       string the legacy DTO contract expects.</li>
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

    private final ProblemOwnerPort problemOwnerPort;
    private final AdminProblemPort problemPort;

    @Override
    @Transactional
    public ImportProblemsResponseDTO importProblems(ImportProblemsRequestDTO request) {
        List<ImportProblemItemDTO> items = request.getProblems();
        List<ImportProblemsResponseDTO.ImportResultItem> results = new ArrayList<>(items.size());
        Map<ImportAction, Integer> counters = new EnumMap<>(ImportAction.class);
        int failed = 0;

        for (ImportProblemItemDTO item : items) {
            ImportAction action;
            try {
                action = applyItem(request.getOnConflict(), item);
            } catch (Exception e) {
                log.error("Import failed for problem slug={}: {}", item.getSlug(), e.getMessage(), e);
                failed++;
                results.add(new ImportProblemsResponseDTO.ImportResultItem(item.getSlug(), false, e.getMessage(), null));
                continue;
            }
            counters.merge(action, 1, Integer::sum);
            results.add(success(item.getSlug(), action));
        }

        return new ImportProblemsResponseDTO(
                items.size(),
                counters.getOrDefault(ImportAction.CREATED, 0),
                counters.getOrDefault(ImportAction.UPDATED, 0),
                counters.getOrDefault(ImportAction.SKIPPED, 0),
                failed,
                results);
    }

    private ImportAction applyItem(String onConflictWire, ImportProblemItemDTO item) {
        Optional<Problem> existing = problemPort.findBySlug(item.getSlug());
        if (existing.isEmpty()) {
            // P3-BURNDOWN-001: row construction + import defaults + insert all
            // live behind the owner port; admin never touches ProblemMapper.
            problemOwnerPort.insertImportedProblem(item.getSlug(), item.getTitle(), item.getDifficulty(),
                    item.getStatus(), item.getIsPremium(), item.getIsPublished());
            return ImportAction.CREATED;
        }
        return resolveConflict(ConflictPolicy.from(onConflictWire), existing.get(), item);
    }

    private ImportAction resolveConflict(
            ConflictPolicy policy, Problem existing, ImportProblemItemDTO item) {
        return switch (policy) {
            case UPDATE -> {
                problemOwnerPort.applyImportedUpdate(existing.getId(), item.getTitle(), item.getDifficulty(),
                        item.getStatus(), item.getIsPremium(), item.getIsPublished());
                yield ImportAction.UPDATED;
            }
            case CREATE_NEW -> {
                problemOwnerPort.insertImportedProblem(item.getSlug() + "-" + System.currentTimeMillis(),
                        item.getTitle(), item.getDifficulty(), item.getStatus(),
                        item.getIsPremium(), item.getIsPublished());
                yield ImportAction.CREATED;
            }
            case SKIP -> ImportAction.SKIPPED;
        };
    }


    private ImportProblemsResponseDTO.ImportResultItem success(String slug, ImportAction action) {
        return new ImportProblemsResponseDTO.ImportResultItem(slug, true, null, action.wireValue());
    }
}