package com.ulticode.modules.submission.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * Canonical per-case scope enum for {@code submissions.test_details[*].caseScope}
 * (P0-1 / ADR-001 trajectory).
 *
 * <p>Two durable values map onto the {@code test_cases.is_sample} / {@code is_hidden}
 * columns:
 * <ul>
 *   <li>{@link #SAMPLE} — public example shown in the problem statement
 *       (corresponds to {@code is_sample=true, is_hidden=false}).</li>
 *   <li>{@link #HIDDEN} — private judge case used for verdict but never exposed
 *       to end users (corresponds to {@code is_sample=false, is_hidden=true}).</li>
 * </ul>
 *
 * <p><b>Legacy compatibility:</b> rows written before P0-1 ship have no {@code caseScope}
 * field (Jackson deserializes to {@code null}). The projection layer
 * ({@code SubmissionServiceImpl#toUserSampleCases}) treats {@code null} as a legacy
 * sample so existing {@code test_details} JSON stays queryable. We intentionally do
 * <b>not</b> add a {@code LEGACY_SAMPLE} enum value: persisting a third value into
 * JSON would break the canonical {@code SAMPLE|HIDDEN} contract and force every
 * consumer to handle it.
 *
 * <p><b>Wire contract</b> (changes require a new ADR):
 * <ul>
 *   <li>{@link #wireValue()} is the durable JSON value and database string.</li>
 *   <li>{@code name()} / {@code ordinal()} are JVM-internal only.</li>
 *   <li>{@link #isUserVisible(CaseScope)} is the single source of truth for "is this
 *       case allowed to appear in a non-admin response".</li>
 * </ul>
 */
@Getter
public enum CaseScope {

    /** Public sample case shown in the problem statement. */
    SAMPLE("SAMPLE"),

    /** Hidden judge case, never returned to non-admin endpoints. */
    HIDDEN("HIDDEN");

    private final String wireValue;

    CaseScope(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the durable JSON / database representation of this scope.
     * Always emit this when serializing to {@code test_details} JSON or any
     * other persisted field.
     */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Parses a wire value (from JSON, DB row, or feature flag) back into the enum.
     * Case-sensitive to match the canonical {@code SAMPLE|HIDDEN} contract.
     *
     * @throws IllegalArgumentException for unknown wire values
     */
    @JsonCreator
    public static CaseScope fromWire(String wireValue) {
        if (wireValue == null) {
            return null;
        }
        for (CaseScope scope : values()) {
            if (scope.wireValue.equals(wireValue)) {
                return scope;
            }
        }
        throw new IllegalArgumentException(
                "Unknown CaseScope wire value: '" + wireValue + "' (expected SAMPLE or HIDDEN)");
    }

    /**
     * Single source of truth for whether a case may appear in a non-admin response.
     * {@code null} (legacy JSON without {@code caseScope}) is treated as SAMPLE for
     * backward compatibility; HIDDEN is never user-visible.
     */
    public static boolean isUserVisible(CaseScope scope) {
        return scope != HIDDEN;
    }
}
