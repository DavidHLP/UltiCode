package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Admin-facing view of a contest as returned by
 * {@link com.ulticode.app.api.service.ContestAdministrationService}.
 *
 * <p>Kept intentionally narrow: the id, title, and current lifecycle
 * status (including the transient FINISHING state). The Admin BFF fetches the deep editor view (description,
 * problems, scoring rule, tags) from App's HTTP API, not from the Dubbo
 * contract; this projection is the small confirmation payload the RPC
 * returns to prove the write succeeded.
 *
 * <p>Unlike {@link ProblemAdminViewDTO}, this DTO has no {@code version}
 * field: the Contest entity has no {@code @Version} column and is
 * state-machine driven ({@code status} &isin; DRAFT, UPCOMING, RUNNING, FINISHING,
 * FINISHED, CANCELLED). Contest lifecycle transitions are protected by
 * state validation (e.g. must be UPCOMING to start), not optimistic
 * locking.
 */
public record ContestAdminViewDTO(
        String contestId,
        String title,
        String status) implements Serializable {
}
