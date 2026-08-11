package com.ulticode.modules.problem.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.app.api.service.ProblemOwnerPort;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P3-OWNER-001-A: default {@link ProblemOwnerPort} implementation.
 * Lives in the problem module (the OWNER), uses the problem
 * module's own {@link ProblemMapper} for the actual writes. The
 * legacy admin code never imports this class directly; it
 * injects the port interface so the seam is a real abstraction.
 *
 * <p>{@code @Transactional} on each write method so the port owns
 * its transaction boundary. A future Dubbo provider (P4-RPC-001)
 * is a one-class swap: the default adapter is replaced by a
 * {@code @DubboService} consumer wrapper, the wire shape is
 * unchanged.
 *
 * <p>The port interface lives in {@code backend-app-api}
 * ({@link com.ulticode.app.api.service.ProblemOwnerPort}) so
 * cross-module consumers inject the shared contract without
 * importing the problem module's implementation package.
 *
 * <p>{@code restoreDeletedByIds} and {@code moderateProblems} are
 * all-or-nothing: if any single row update fails, the transaction
 * rolls back the whole batch.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DefaultProblemOwnerPort implements ProblemOwnerPort {

    private final ProblemMapper problemMapper;

    @Override
    @Transactional
    public void flagProblem(Long id, String reason, String reportedBy) {
        final int affected = problemMapper.flagProblem(id, reason, reportedBy);
        log.info("ProblemOwnerPort.flagProblem id={} reason={} reporter={} affected={}",
                id, reason, reportedBy, affected);
    }

    @Override
    @Transactional
    public void moderateProblem(Long id, String status, String notes, String reviewedBy) {
        final int affected = problemMapper.moderateProblem(id, status, notes, reviewedBy);
        log.info("ProblemOwnerPort.moderateProblem id={} status={} reviewer={} affected={}",
                id, status, reviewedBy, affected);
    }

    @Override
    @Transactional
    public int restoreDeletedByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        final int restored = problemMapper.restoreDeletedByIds(ids);
        log.info("ProblemOwnerPort.restoreDeletedByIds count={} restored={}", ids.size(), restored);
        return restored;
    }

    @Override
    @Transactional
    public int moderateProblems(List<Long> ids, String status, String notes, String reviewedBy) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // The mapper method is named batchModerateProblems but the
        // port's verb is moderateProblems to match the singular /
        // plural pair (moderateProblem / moderateProblems).
        final int affected = problemMapper.batchModerateProblems(ids, status, notes, reviewedBy);
        log.info("ProblemOwnerPort.moderateProblems count={} status={} reviewer={} affected={}",
                ids.size(), status, reviewedBy, affected);
        return affected;
    }

    @Override
    @Transactional
    public void updateDifficulty(Long id, String difficulty) {
        if (id == null || difficulty == null || difficulty.isBlank()) {
            return;
        }
        // P3-OWNER-001-A: the port owns the difficulty write; the
        // existence + read-after-write + business validation lives
        // in the admin caller (it has the user-facing error path).
        // The mapper's BaseMapper.updateById takes an entity; the
        // port is a Command surface, so we denormalize to a
        // lightweight Problem shell here. The read-back below
        // matches the original AdminProblemServiceImpl.edit branch.
        final int affected = problemMapper.updateDifficulty(id, difficulty);
        log.info("ProblemOwnerPort.updateDifficulty id={} difficulty={} affected={}",
                id, difficulty, affected);
    }

    private static final String IMPORT_DEFAULT_STATUS = "todo";

    @Override
    @Transactional
    public void insertImportedProblem(String slug, String title, String difficulty, String status,
                                      Boolean isPremium, Boolean isPublished) {
        createImportedProblem(slug, title, difficulty, status, isPremium, isPublished);
    }

    private Problem createImportedProblem(String slug, String title, String difficulty, String status,
                                          Boolean isPremium, Boolean isPublished) {
        Problem problem = new Problem();
        problem.setSlug(slug);
        problem.setTitle(title);
        problem.setDifficulty(difficulty);
        problem.setStatus(status != null ? status : IMPORT_DEFAULT_STATUS);
        problem.setIsPremium(isPremium != null ? isPremium : false);
        problem.setIsPublished(isPublished != null ? isPublished : false);
        problem.setHasSolution(false);
        problem.setIsFlagged(false);
        problem.setIsDeleted(false);
        problem.setVersion(1);
        problemMapper.insert(problem);
        log.info("ProblemOwnerPort.insertImportedProblem slug={} id={}", slug, problem.getId());
        return problem;
    }

    @Override
    @Transactional
    public void applyImportedUpdate(Long id, String title, String difficulty, String status,
                                    Boolean isPremium, Boolean isPublished) {
        if (id == null) {
            return;
        }
        Problem existing = problemMapper.selectById(id);
        if (existing == null) {
            // Row vanished between the admin caller's findBySlug and this
            // update: the legacy detached-entity updateById would have
            // affected zero rows too, so a no-op preserves semantics.
            return;
        }
        if (StringUtils.hasText(title)) {
            existing.setTitle(title);
        }
        if (StringUtils.hasText(difficulty)) {
            existing.setDifficulty(difficulty);
        }
        if (StringUtils.hasText(status)) {
            existing.setStatus(status);
        }
        if (isPremium != null) {
            existing.setIsPremium(isPremium);
        }
        if (isPublished != null) {
            existing.setIsPublished(isPublished);
        }
        problemMapper.updateById(existing);
        log.info("ProblemOwnerPort.applyImportedUpdate id={}", id);
    }

    @Override
    public List<ImportWriteResult> applyImportedBatch(List<ImportWriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        if (requests.size() > MAX_IMPORT_SIZE) {
            throw new IllegalArgumentException("Too many problem import writes");
        }
        List<ImportWriteResult> results = new ArrayList<>(requests.size());
        Map<String, Long> createdIds = new HashMap<>();
        for (ImportWriteRequest request : requests) {
            if (request == null) {
                results.add(new ImportWriteResult(null, false, "Import request is null"));
                continue;
            }
            try {
                if (request.create()) {
                    Problem created = createImportedProblem(request.slug(), request.title(),
                            request.difficulty(), request.status(), request.isPremium(),
                            request.isPublished());
                    if (created.getId() != null) {
                        createdIds.put(request.slug(), created.getId());
                    }
                } else {
                    Long id = request.id() == null ? createdIds.get(request.slug()) : request.id();
                    if (id != null) {
                        applyImportedUpdate(id, request.title(), request.difficulty(),
                                request.status(), request.isPremium(), request.isPublished());
                    } else {
                        Problem existing = problemMapper.selectOne(
                                new LambdaQueryWrapper<Problem>()
                                        .eq(Problem::getSlug, request.slug()));
                        if (existing == null) {
                            Problem created = createImportedProblem(request.slug(), request.title(),
                                    request.difficulty(), request.status(), request.isPremium(),
                                    request.isPublished());
                            if (created.getId() != null) {
                                createdIds.put(request.slug(), created.getId());
                            }
                        } else {
                            applyImportedUpdate(existing.getId(), request.title(), request.difficulty(),
                                    request.status(), request.isPremium(), request.isPublished());
                        }
                    }
                }
                results.add(new ImportWriteResult(request.key(), true, null));
            } catch (Exception e) {
                String message = e.getMessage();
                log.error("ProblemOwnerPort.applyImportedBatch key={} slug={} failed: {}",
                        request.key(), request.slug(), message, e);
                results.add(new ImportWriteResult(request.key(), false, message));
            }
        }
        return results;
    }

    @Override
    public String resolveAuthorId(String id) {
        var problem = problemMapper.selectById(id);
        return problem != null ? problem.getPublishedBy() : null;
    }

    @Override
    public void updateModerationFlag(String id, boolean isFlagged, String reason) {
        problemMapper.updateFlagStatus(id, isFlagged, reason);
    }
}
