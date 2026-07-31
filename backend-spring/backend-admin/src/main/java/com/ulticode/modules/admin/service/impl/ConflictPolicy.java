package com.ulticode.modules.admin.service.impl;

/**
 * Conflict resolution policy for the problem batch-import module.
 *
 * <p>Promoted from raw {@code String} so the seam's vocabulary is typed:
 * unknown / null policies fold to {@link #SKIP}, preserving the legacy
 * default-branch behaviour.
 *
 * @author ulticode
 */
enum ConflictPolicy {
    SKIP,
    UPDATE,
    CREATE_NEW;

    /**
     * Map the wire string (carried on {@code ImportProblemsRequestDTO.onConflict})
     * to the typed policy. Unknown / null values fold to {@link #SKIP}.
     */
    static ConflictPolicy from(String wire) {
        if (wire == null) {
            return SKIP;
        }
        return switch (wire) {
            case "update" -> UPDATE;
            case "create_new" -> CREATE_NEW;
            default -> SKIP;
        };
    }
}