package com.ulticode.app.api.service;

import java.io.Serializable;
import java.util.List;

/**
 * Owner-only write surface for the {@code problems} table.
 *
 * <p>The Problem provider owns the implementation. Consumers use this
 * primitive contract instead of importing the Problem entity or mapper. The
 * methods retain the current affected-row and no-op semantics so the later
 * implementation move does not change moderation or import behavior.
 */
public interface ProblemOwnerPort {

    /**
     * Maximum number of rows accepted by one import batch RPC.
     */
    int MAX_IMPORT_SIZE = 500;

    /**
     * Entity-free import write request. {@code create} selects insert versus
     * update; update requests carry the existing problem id when one is known.
     * The key is owned by the caller and is echoed by the result.
     */
    record ImportWriteRequest(String key, boolean create, Long id, String slug,
                              String title, String difficulty, String status,
                              Boolean isPremium, Boolean isPublished) implements Serializable {
        private static final long serialVersionUID = 1L;
}

    /**
     * Per-row outcome for an import write. A failed row does not prevent later
     * requests in the same bounded batch from being attempted.
     */
    record ImportWriteResult(String key, boolean success, String error) implements Serializable {
        private static final long serialVersionUID = 1L;
}

    /**
     * Apply an import batch in request order, isolating failures per row.
     *
     * @param requests bounded import writes; null/empty means no writes
     * @return one result per attempted request, keyed by {@link ImportWriteRequest#key()}
     */
    List<ImportWriteResult> applyImportedBatch(List<ImportWriteRequest> requests);

    /**
     * Resolve the author of a problem without mutating it.
     *
     * @param id problem ID in the cross-module string representation
     * @return the publishing user ID, or {@code null} when the problem is absent
     */
    String resolveAuthorId(String id);

    /**
     * Set the moderation flag on a problem.
     *
     * @param id problem ID in the cross-module string representation
     * @param isFlagged new flag state
     * @param reason flag reason, or {@code null} when clearing the flag
     */
    void updateModerationFlag(String id, boolean isFlagged, String reason);

    /**
     * Flag one problem for moderation.
     */
    void flagProblem(Long id, String reason, String reportedBy);

    /**
     * Apply a moderation decision to one problem.
     */
    void moderateProblem(Long id, String status, String notes, String reviewedBy);

    /**
     * Restore soft-deleted problems in bulk.
     *
     * @return the number of rows actually restored
     */
    int restoreDeletedByIds(List<Long> ids);

    /**
     * Apply one moderation decision to many problems in one transaction.
     *
     * @return the number of rows actually updated
     */
    int moderateProblems(List<Long> ids, String status, String notes, String reviewedBy);

    /**
     * Update one problem's difficulty. Repeating the current value is a no-op.
     */
    void updateDifficulty(Long id, String difficulty);

    /**
     * Insert an imported problem with the provider's import defaults.
     */
    void insertImportedProblem(String slug, String title, String difficulty, String status,
                               Boolean isPremium, Boolean isPublished);

    /**
     * Apply the non-blank/non-null fields of an imported conflict update.
     */
    void applyImportedUpdate(Long id, String title, String difficulty, String status,
                             Boolean isPremium, Boolean isPublished);
}
