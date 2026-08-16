package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Admin-facing view of a problem as returned by
 * {@link com.ulticode.app.api.service.ProblemAdministrationService}.
 *
 * <p>Kept intentionally narrow: the id, slug, title, current version
 * and the lifecycle status. The Admin BFF fetches the deep editor view
 * (statements, examples, tags) from App's HTTP API, not from the
 * Dubbo contract; this projection is the small confirmation payload
 * the RPC returns to prove the write succeeded.
 */
public record ProblemAdminViewDTO(
        String problemId,
        String slug,
        String title,
        long version,
        String status) implements Serializable {
    private static final long serialVersionUID = 1L;

}