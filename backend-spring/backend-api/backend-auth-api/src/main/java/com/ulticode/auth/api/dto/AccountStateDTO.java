package com.ulticode.auth.api.dto;

/**
 * Projection of an account's lifecycle state as returned by
 * {@link com.ulticode.auth.api.service.AccountAdministrationService#changeState}.
 *
 * <p>Kept intentionally narrow: only the account id, the effective
 * state (active / banned / disabled) and the new expected version.
 * Audit timestamps and ban rationale are surfaced through the auth
 * service's own audit log; the wire shape here is the minimum needed
 * for the Admin BFF to confirm its write.
 */
public record AccountStateDTO(
        String accountId,
        boolean active,
        boolean banned,
        long version) {
}