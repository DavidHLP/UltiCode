package com.ulticode.modules.admin.port.adapter;

/**
 * Immutable runtime policy for an admin owner write seam.
 *
 * @param domain owner domain name
 * @param flagKey property key when the seam is flag-controlled, or {@code null}
 * @param defaultEnabled configured default for the flag
 * @param enabled resolved flag value
 * @param policy behavior when the flag is disabled
 */
public record OwnerCutoverGate(
        String domain,
        String flagKey,
        boolean defaultEnabled,
        boolean enabled,
        Policy policy) {

    public enum Policy {
        FAIL_CLOSED,
        DELEGATE_LOCAL,
        ALWAYS_REMOTE,
        /** Notification cutover twin deleted; writes use the single remote path. */
        CUTOVER_REMOVED
    }

    /** Resolves the write-routing decision every surviving write path consumes. */
    public OwnerCutoverDecision decide() {
        return switch (policy) {
            case ALWAYS_REMOTE, CUTOVER_REMOVED -> OwnerCutoverDecision.REMOTE;
            case FAIL_CLOSED -> enabled ? OwnerCutoverDecision.REMOTE : OwnerCutoverDecision.DENY;
            case DELEGATE_LOCAL -> enabled ? OwnerCutoverDecision.REMOTE : OwnerCutoverDecision.LOCAL;
        };
    }
}
