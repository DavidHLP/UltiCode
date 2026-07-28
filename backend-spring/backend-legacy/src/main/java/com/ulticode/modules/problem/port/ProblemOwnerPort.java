package com.ulticode.modules.problem.port;

import java.util.List;

/**
 * P3-OWNER-001-A: owner-only write surface for the {@code problems}
 * row that lives in the problem module.
 *
 * <p>Before this port, the legacy {@code AdminProblemServiceImpl}
 * reached directly into {@link com.ulticode.modules.problem.mapper.ProblemMapper}
 * for flag / moderate / restore / batch-moderate. The Admin
 * module's P3-OWNER-001-A boundary now forbids foreign-mapper
 * WRITE methods (ArchUnit rule P3-OWNER-001-F), so every admin
 * caller of these writes must go through this port. The
 * implementation lives in the problem module; admin code never
 * imports the underlying mapper or the {@code Problem} entity
 * across the port boundary.
 *
 * <p>Read methods (toVO, findBySlug, findSubmissionsByProblemId)
 * and the existing publish / unpublish / delete write methods
 * stay on {@code com.ulticode.modules.admin.port.AdminProblemPort}
 * for the AdminReadModel seam. The P3-OWNER-001 follow-up will
 * consolidate the two ports; for now the two are clearly split
 * (this port is the OWNER write surface, the admin port is the
 * READ + lifecycle-publish surface).
 *
 * <p>Commands are primitive shapes (id / status / reason) so the
 * port is RPC-friendly: a future Dubbo provider (P4-RPC-001)
 * replaces the default adapter with a {@code @DubboService} and
 * the wire shape is unchanged.
 */
public interface ProblemOwnerPort {

    /**
     * Flag a problem for moderation. Sets {@code is_flagged = true}
     * and {@code flag_reason} on the row; idempotent (no-op when
     * already flagged with the same reason).
     */
    void flagProblem(Long id, String reason, String reportedBy);

    /**
     * Apply a moderation decision to a problem. Sets
     * {@code moderation_status} and {@code moderation_notes}.
     */
    void moderateProblem(Long id, String status, String notes, String reviewedBy);

    /**
     * Restore soft-deleted problems in bulk. Returns the number
     * of rows actually restored.
     */
    int restoreDeletedByIds(List<Long> ids);

    /**
     * Apply a moderation decision to many problems in one
     * transaction. Returns the number of rows actually updated.
     */
    int moderateProblems(List<Long> ids, String status, String notes, String reviewedBy);

    /**
     * Update a single problem's difficulty. Idempotent: writing
     * the same difficulty the row already has is a no-op.
     */
    void updateDifficulty(Long id, String difficulty);

    /**
     * P3-BURNDOWN-001: insert a newly-imported problem row. The owner
     * applies the import defaults: {@code status} falls back to
     * {@code "todo"} when null, {@code isPremium} / {@code isPublished}
     * default to false, and the row always starts with
     * {@code has_solution=false, is_flagged=false, is_deleted=false, version=1}.
     */
    void insertImportedProblem(String slug, String title, String difficulty, String status,
                               Boolean isPremium, Boolean isPublished);

    /**
     * P3-BURNDOWN-001: apply import conflict-update fields onto an
     * existing row. String fields are only written when non-blank,
     * Boolean fields when non-null (the legacy PartialUpdate semantics);
     * a vanished row is a no-op, matching the previous detached-entity
     * {@code updateById} outcome of zero affected rows.
     */
    void applyImportedUpdate(Long id, String title, String difficulty, String status,
                             Boolean isPremium, Boolean isPublished);
}
