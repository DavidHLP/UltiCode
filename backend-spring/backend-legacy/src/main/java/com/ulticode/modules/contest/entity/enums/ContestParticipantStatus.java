package com.ulticode.modules.contest.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Canonical {@link com.ulticode.modules.contest.entity.ContestParticipant} status.
 *
 * <p>The persisted column is a {@code String} (see {@code V20260602_120000__Create_All_Tables.sql}),
 * but every Java caller MUST round-trip through this enum via
 * {@link #wireValue()} / {@link #fromWire(String)} so the durable wire value
 * ({@code REGISTERED}, {@code STARTED}, {@code FINISHED}, {@code DISQUALIFIED}) has
 * a single source of truth. The mapper keeps its atomic SQL guards
 * ({@code WHERE status = 'REGISTERED'}, etc.) as string literals — those are
 * the database contract, and centralising them here would tie the entity to a
 * specific JDBC dialect.
 */
public enum ContestParticipantStatus {
    /** Registered for the contest; not yet eligible to submit. */
    REGISTERED("REGISTERED"),
    /** Active participant; eligible to submit; clock running. */
    STARTED("STARTED"),
    /** Submission window closed; row is now rating-eligible. */
    FINISHED("FINISHED"),
    /** Disqualified by an admin; row is terminal. */
    DISQUALIFIED("DISQUALIFIED");

    private final String wireValue;

    ContestParticipantStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Durable string value used in the {@code status} column, in JSON payloads,
     * and in atomic-UPDATE guards. Always emit this when serialising or when
     * building a literal that the SQL mapper will compare against the column.
     */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Parses the persisted column value back into the enum. Case-sensitive to
     * match the wire contract; throws for unknown values so a bad row
     * surfaces as a startup / read failure rather than a silent default.
     */
    @JsonCreator
    public static ContestParticipantStatus fromWire(String wireValue) {
        if (wireValue == null) {
            return null;
        }
        for (ContestParticipantStatus status : values()) {
            if (status.wireValue.equals(wireValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException(
                "Unknown ContestParticipantStatus wire value: '" + wireValue
                        + "' (expected REGISTERED, STARTED, FINISHED, or DISQUALIFIED)");
    }
}
