package com.ulticode.modules.admin.service.impl;

/**
 * Per-item outcome of one row in the problem batch-import module.
 *
 * <p>Promoted from raw {@code String} so the seam's outcome vocabulary is
 * typed and the result counters can accumulate via an {@link java.util.EnumMap}.
 * Wire strings ({@link #wireValue()}) stay backwards-compatible with the
 * pre-refactor DTO contract ({@code ImportProblemsResponseDTO.ImportResultItem.action}).
 *
 * @author ulticode
 */
enum ImportAction {
    CREATED("created"),
    UPDATED("updated"),
    SKIPPED("skipped");

    private final String wireValue;

    ImportAction(String wireValue) {
        this.wireValue = wireValue;
    }

    String wireValue() {
        return wireValue;
    }
}