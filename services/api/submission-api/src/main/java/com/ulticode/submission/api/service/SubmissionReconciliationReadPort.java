package com.ulticode.submission.api.service;

import com.ulticode.submission.api.dto.SubmissionUserReferenceCountDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bounded, owner-local facts used by Admin reconciliation.
 *
 * <p>{@code createdSince == null} requests a full scan. A non-null timestamp
 * requests the incremental window from that point onward. Both modes page by
 * the last account id so the consumer never receives unbounded Submission
 * rows or imports owner entities/SQL.
 */
public interface SubmissionReconciliationReadPort {

    int MAX_PAGE_SIZE = 500;

    /**
     * Return at most {@code limit} grouped user references after
     * {@code afterAccountId}.
     *
     * @param afterAccountId  exclusive account-id cursor; use empty for first page
     * @param createdSince    null for full history, otherwise inclusive creation watermark
     * @param limit           requested page size, bounded by {@link #MAX_PAGE_SIZE}
     * @return non-null, account-id ordered facts
     */
    List<SubmissionUserReferenceCountDTO> findUserReferenceCounts(
            String afterAccountId,
            LocalDateTime createdSince,
            int limit);
}
