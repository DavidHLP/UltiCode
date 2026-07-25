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

    private static final String DEFAULT_STATUS = "todo";

    private final ProblemMapper problemMapper;
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
            problemMapper.insert(createNew(item));
            return ImportAction.CREATED;
        }
        return resolveConflict(ConflictPolicy.from(onConflictWire), existing.get(), item);
    }

    private ImportAction resolveConflict(
            ConflictPolicy policy, Problem existing, ImportProblemItemDTO item) {
        return switch (policy) {
            case UPDATE -> {
                applyPartialUpdate(existing, item);
                problemMapper.updateById(existing);
                yield ImportAction.UPDATED;
            }
            case CREATE_NEW -> {
                Problem created = createNew(item);
                created.setSlug(item.getSlug() + "-" + System.currentTimeMillis());
                problemMapper.insert(created);
                yield ImportAction.CREATED;
            }
            case SKIP -> ImportAction.SKIPPED;
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

    private ImportProblemsResponseDTO.ImportResultItem success(String slug, ImportAction action) {
        return new ImportProblemsResponseDTO.ImportResultItem(slug, true, null, action.wireValue());
    }
}