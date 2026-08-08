package com.ulticode.modules.submission.fence;

import com.ulticode.domain.submission.enums.SubmissionStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure-function state machine for submission verdict transitions (ADR-003 §2.5).
 *
 * <p>Two distinct transition channels exist:
 * <ul>
 *   <li>{@link #canSystemTransition(SubmissionStatus, SubmissionStatus)} — the
 *       worker / reaper channel. Only {@code IN_FLIGHT} statuses have outgoing
 *       edges; terminal statuses are sticky on this channel.</li>
 *   <li>{@link #canAdminRejudgeFrom(SubmissionStatus)} — the admin rejudge
 *       channel. Any terminal status may be reset back to Pending. This is a
 *       privileged, explicit cross-terminal path and does NOT go through the
 *       SYSTEM_ALLOWED table.</li>
 * </ul>
 *
 * <p>Implementation note: {@link EnumMap} is used (instead of {@code Map.of})
 * because the {@code java-map-of-null-safety} rule forbids {@code Map.of} when
 * values may be {@code null} — here the value sets are never null, but
 * {@link EnumMap} also gives us keyed-by-enum type safety and avoids boxing.
 * The {@link EnumSet}s back the membership lookups for O(1) reads.
 */
public final class SubmissionStateMachine {

    private SubmissionStateMachine() {
        // Pure-function utility; no instances.
    }

    /**
     * System-initiated transitions out of each source status. Keys are
     * restricted to {@code IN_FLIGHT} statuses; terminal statuses intentionally
     * have no entry, which {@link #canSystemTransition} interprets as "no
     * system path out of this state".
     */
    private static final Map<SubmissionStatus, Set<SubmissionStatus>> SYSTEM_ALLOWED = new EnumMap<>(SubmissionStatus.class);

    /**
     * Terminal statuses from which an admin may trigger a rejudge. Covers every
     * {@code TERMINAL_GOOD} / {@code TERMINAL_BAD} / {@code TERMINAL_INFRA}
     * status — rejudge is the privileged escape hatch from any final state.
     */
    private static final Set<SubmissionStatus> ADMIN_REJUDGE_FROM;

    static {
        // Pending -> {Judging, System Error}. System Error is allowed so a
        // dispatcher/worker infra failure before judging can mark the row.
        SYSTEM_ALLOWED.put(
                SubmissionStatus.PENDING,
                EnumSet.of(
                        SubmissionStatus.JUDGING,
                        SubmissionStatus.SYSTEM_ERROR));
        // Judging -> any terminal verdict, plus back to Pending via lease
        // expiry (the reaper path). The reaper's bumpGenerationAndReset is the
        // only legitimate Judging -> Pending transition.
        SYSTEM_ALLOWED.put(
                SubmissionStatus.JUDGING,
                EnumSet.of(
                        SubmissionStatus.ACCEPTED,
                        SubmissionStatus.PRESENTATION_ERROR,
                        SubmissionStatus.WRONG_ANSWER,
                        SubmissionStatus.TIME_LIMIT_EXCEEDED,
                        SubmissionStatus.MEMORY_LIMIT_EXCEEDED,
                        SubmissionStatus.OUTPUT_LIMIT_EXCEEDED,
                        SubmissionStatus.RUNTIME_ERROR,
                        SubmissionStatus.COMPILE_ERROR,
                        SubmissionStatus.SANDBOX_ERROR,
                        SubmissionStatus.SYSTEM_ERROR,
                        SubmissionStatus.PENDING));

        ADMIN_REJUDGE_FROM = EnumSet.of(
                SubmissionStatus.ACCEPTED,
                SubmissionStatus.PRESENTATION_ERROR,
                SubmissionStatus.WRONG_ANSWER,
                SubmissionStatus.TIME_LIMIT_EXCEEDED,
                SubmissionStatus.MEMORY_LIMIT_EXCEEDED,
                SubmissionStatus.OUTPUT_LIMIT_EXCEEDED,
                SubmissionStatus.RUNTIME_ERROR,
                SubmissionStatus.COMPILE_ERROR,
                SubmissionStatus.SANDBOX_ERROR,
                SubmissionStatus.SYSTEM_ERROR);
    }

    /**
     * Whether the judge worker / lease reaper may transition a submission from
     * {@code from} to {@code to}. Returns {@code false} for any transition
     * originating from a terminal status (system channel cannot escape
     * terminals) and for any target not in the source's allowed set.
     *
     * @param from current status
     * @param to   candidate next status
     * @return true if the system channel permits this transition
     */
    public static boolean canSystemTransition(SubmissionStatus from, SubmissionStatus to) {
        if (from == null || to == null) {
            return false;
        }
        Set<SubmissionStatus> allowed = SYSTEM_ALLOWED.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Whether an admin rejudge may reset a submission from {@code from}. Any
     * terminal status is rejudgeable; IN_FLIGHT statuses are handled by the
     * rejudge-on-JUDGING force-lease-expiry path rather than this predicate.
     *
     * @param from current status
     * @return true if the status is a terminal from which rejudge is permitted
     */
    public static boolean canAdminRejudgeFrom(SubmissionStatus from) {
        if (from == null) {
            return false;
        }
        return ADMIN_REJUDGE_FROM.contains(from);
    }
}
