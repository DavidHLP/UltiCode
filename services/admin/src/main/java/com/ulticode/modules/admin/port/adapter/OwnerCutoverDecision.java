package com.ulticode.modules.admin.port.adapter;

/**
 * Write-routing decision for an owner cutover seam.
 *
 * <ul>
 *   <li>{@code REMOTE} — the write goes through the remote owner provider.</li>
 *   <li>{@code LOCAL} — the seam still serves its local implementation.</li>
 *   <li>{@code DENY} — no write path is enabled; the caller must fail closed.</li>
 * </ul>
 */
public enum OwnerCutoverDecision {
    REMOTE,
    LOCAL,
    DENY
}
