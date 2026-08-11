package com.ulticode.modules.admin.service.impl;

import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.app.api.service.ProblemOwnerPort;
import com.ulticode.app.api.service.ProblemOwnerPort.ImportWriteRequest;
import com.ulticode.app.api.service.ProblemOwnerPort.ImportWriteResult;
import com.ulticode.modules.admin.dto.problem.ImportProblemItemDTO;
import com.ulticode.modules.admin.dto.problem.ImportProblemsRequestDTO;
import com.ulticode.modules.admin.dto.problem.ImportProblemsResponseDTO;
import com.ulticode.modules.admin.service.ProblemImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *   <li>create/update identity — ordered, entity-free write requests carry
 *       the operation and fields to the owner batch port;</li>
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
 * result-item shape. The slug existence check now flows through the public
 * {@link ProblemAdminReadPort} instead of the App-private entity lookup.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemImportServiceImpl implements ProblemImportService {

    private final ProblemOwnerPort problemOwnerPort;
    private final ProblemAdminReadPort problemReadPort;

    @Override
    public ImportProblemsResponseDTO importProblems(ImportProblemsRequestDTO request) {
        List<ImportProblemItemDTO> items = request.getProblems();
        if (items == null || items.isEmpty()) {
            return response(0, new EnumMap<>(ImportAction.class), 0, List.of());
        }
        if (items.size() > ProblemOwnerPort.MAX_IMPORT_SIZE) {
            throw new IllegalArgumentException("Too many problems to import");
        }

        List<ImportProblemsResponseDTO.ImportResultItem> results =
                new ArrayList<>(Collections.nCopies(items.size(), null));
        Map<ImportAction, Integer> counters = new EnumMap<>(ImportAction.class);
        List<String> slugs = items.stream()
                .map(item -> item == null ? null : item.getSlug())
                .toList();

        List<ProblemAdminRowDTO> found;
        try {
            found = problemReadPort.findBySlugs(slugs);
        } catch (Exception e) {
            String error = errorMessage(e);
            log.error("Import batch read failed: {}", error, e);
            for (int i = 0; i < items.size(); i++) {
                ImportProblemItemDTO item = items.get(i);
                results.set(i, failure(item == null ? null : item.getSlug(), error));
            }
            return response(items.size(), counters, items.size(), results);
        }

        Map<String, ProblemAdminRowDTO> existingBySlug = new HashMap<>();
        if (found != null) {
            for (ProblemAdminRowDTO row : found) {
                if (row != null && row.slug() != null) {
                    existingBySlug.putIfAbsent(row.slug(), row);
                }
            }
        }

        Set<String> createdSlugs = new HashSet<>();
        List<PendingWrite> pending = new ArrayList<>();
        int failed = 0;
        ConflictPolicy policy = ConflictPolicy.from(request.getOnConflict());

        for (int i = 0; i < items.size(); i++) {
            ImportProblemItemDTO item = items.get(i);
            if (item == null) {
                results.set(i, failure(null, "Import item is null"));
                failed++;
                continue;
            }

            String slug = item.getSlug();
            ProblemAdminRowDTO existing = existingBySlug.get(slug);
            boolean exists = existing != null || createdSlugs.contains(slug);
            String key = Integer.toString(i);

            if (!exists) {
                pending.add(new PendingWrite(key, slug, ImportAction.CREATED,
                        new ImportWriteRequest(key, true, null, slug, item.getTitle(),
                                item.getDifficulty(), item.getStatus(), item.getIsPremium(),
                                item.getIsPublished())));
                createdSlugs.add(slug);
                continue;
            }

            if (policy == ConflictPolicy.SKIP) {
                counters.merge(ImportAction.SKIPPED, 1, Integer::sum);
                results.set(i, success(slug, ImportAction.SKIPPED));
                continue;
            }

            if (policy == ConflictPolicy.UPDATE) {
                pending.add(new PendingWrite(key, slug, ImportAction.UPDATED,
                        new ImportWriteRequest(key, false, existing == null ? null : existing.id(),
                                slug, item.getTitle(), item.getDifficulty(), item.getStatus(),
                                item.getIsPremium(), item.getIsPublished())));
                continue;
            }

            String newSlug = createNewSlug(slug, existingBySlug, createdSlugs);
            pending.add(new PendingWrite(key, slug, ImportAction.CREATED,
                    new ImportWriteRequest(key, true, null, newSlug, item.getTitle(),
                            item.getDifficulty(), item.getStatus(), item.getIsPremium(),
                            item.getIsPublished())));
            createdSlugs.add(newSlug);
        }

        if (!pending.isEmpty()) {
            List<ImportWriteResult> writeResults;
            try {
                writeResults = problemOwnerPort.applyImportedBatch(
                        pending.stream().map(PendingWrite::request).toList());
            } catch (Exception e) {
                String error = errorMessage(e);
                log.error("Import batch write failed: {}", error, e);
                for (PendingWrite write : pending) {
                    int index = Integer.parseInt(write.key());
                    results.set(index, failure(write.originalSlug(), error));
                }
                failed += pending.size();
                return response(items.size(), counters, failed, results);
            }

            Map<String, ImportWriteResult> resultsByKey = new HashMap<>();
            if (writeResults != null) {
                for (ImportWriteResult writeResult : writeResults) {
                    if (writeResult != null && writeResult.key() != null) {
                        resultsByKey.putIfAbsent(writeResult.key(), writeResult);
                    }
                }
            }

            for (PendingWrite write : pending) {
                int index = Integer.parseInt(write.key());
                ImportWriteResult writeResult = resultsByKey.get(write.key());
                if (writeResult == null || !writeResult.success()) {
                    String error = writeResult == null ? "Missing import write result" : writeResult.error();
                    results.set(index, failure(write.originalSlug(), error));
                    log.error("Import failed for problem slug={}: {}", write.originalSlug(), error);
                    failed++;
                    continue;
                }
                counters.merge(write.action(), 1, Integer::sum);
                results.set(index, success(write.originalSlug(), write.action()));
            }
        }

        return response(items.size(), counters, failed, results);
    }

    private String createNewSlug(String slug, Map<String, ProblemAdminRowDTO> existingBySlug,
                                 Set<String> createdSlugs) {
        long suffix = System.currentTimeMillis();
        String candidate;
        do {
            candidate = slug + "-" + suffix++;
        } while (existingBySlug.containsKey(candidate) || createdSlugs.contains(candidate));
        return candidate;
    }

    private ImportProblemsResponseDTO response(
            int total, Map<ImportAction, Integer> counters, int failed,
            List<ImportProblemsResponseDTO.ImportResultItem> results) {
        return new ImportProblemsResponseDTO(
                total,
                counters.getOrDefault(ImportAction.CREATED, 0),
                counters.getOrDefault(ImportAction.UPDATED, 0),
                counters.getOrDefault(ImportAction.SKIPPED, 0),
                failed,
                results);
    }

    private ImportProblemsResponseDTO.ImportResultItem success(String slug, ImportAction action) {
        return new ImportProblemsResponseDTO.ImportResultItem(slug, true, null, action.wireValue());
    }

    private ImportProblemsResponseDTO.ImportResultItem failure(String slug, String error) {
        return new ImportProblemsResponseDTO.ImportResultItem(slug, false, error, null);
    }

    private String errorMessage(Exception e) {
        return e.getMessage();
    }

    private record PendingWrite(String key, String originalSlug, ImportAction action,
                                ImportWriteRequest request) {}
}
