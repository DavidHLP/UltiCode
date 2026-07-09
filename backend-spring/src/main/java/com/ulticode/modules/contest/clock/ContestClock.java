package com.ulticode.modules.contest.clock;

import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Single source of truth for the R6.2 / F-06 contest time arithmetic.
 *
 * <p>Concentrates three clock concepts that were previously open-coded
 * across at least four call sites:
 * <ul>
 *   <li><b>Effective contest start</b> — {@code actualStartTime ?? startTime}.
 *       A real contest transitions {@code UPCOMING → RUNNING} via the
 *       scheduler, which stamps {@code actual_start_time}; until that
 *       transition fires, {@code actual_start_time} is {@code null} and
 *       callers fall back to {@code start_time} (see R6.2).</li>
 *   <li><b>Effective participant end</b> — virtual participants end at
 *       {@code startedAt + durationMinutes}; real participants end at
 *       {@code effectiveStartTime(contest) + durationMinutes}. This
 *       distinction exists because a virtual session runs from the
 *       user's join time, not the contest's nominal start.</li>
 *   <li><b>Effective contest end</b> — for scheduler lifecycle transitions
 *       the admin-set {@code endTime} wins; otherwise
 *       {@code startTime + durationMinutes}.</li>
 * </ul>
 *
 * <p>Pure value class: no Spring state, no side effects, no clock
 * dependency. Callers that need a wall-clock comparison inject
 * {@link java.time.Clock} separately; this class only computes
 * arithmetic on the entity fields.
 *
 * <p>All methods return {@link Optional} to force callers to handle
 * the not-enough-information case (e.g. a draft contest has no
 * {@code startTime} and no {@code actualStartTime}).
 *
 * @author ulticode
 */
@Component
public class ContestClock {

    /**
     * The contest's effective start: {@code actualStartTime ?? startTime}.
     *
     * <p>Use this whenever a "real" contest clock is needed (i.e. not a
     * virtual participant). The fallback to {@code startTime} is what
     * makes pre-scheduler-transition submissions land on the admin's
     * planned schedule instead of throwing.
     */
    public Optional<LocalDateTime> effectiveStartTime(Contest contest) {
        if (contest == null) {
            return Optional.empty();
        }
        LocalDateTime start = contest.getActualStartTime() != null
                ? contest.getActualStartTime()
                : contest.getStartTime();
        return Optional.ofNullable(start);
    }

    /**
     * The end time for a single participant. Virtual sessions run from
     * the participant's {@code startedAt}; real sessions run from the
     * contest's effective start. The duration is
     * {@link Contest#getDurationMinutes()}.
     *
     * <p>Returns {@link Optional#empty()} only when both {@code contest}
     * is missing {@code durationMinutes} and the participant side has no
     * start anchor — the typical "draft contest" / "never started" case.
     */
    public Optional<LocalDateTime> effectiveEndTime(ContestParticipant participant, Contest contest) {
        if (contest == null) {
            return Optional.empty();
        }
        LocalDateTime anchor;
        if (Boolean.TRUE.equals(participant != null ? participant.getIsVirtual() : null)) {
            anchor = participant.getStartedAt();
        } else {
            anchor = effectiveStartTime(contest).orElse(null);
        }
        Integer duration = contest.getDurationMinutes();
        if (anchor == null || duration == null) {
            return Optional.empty();
        }
        return Optional.of(anchor.plusMinutes(duration));
    }

    /**
     * The start anchor for a single participant's elapsed-time measurement.
     * Virtual participants measure from their own {@code startedAt}; real
     * participants measure from the contest's
     * {@link #effectiveStartTime(Contest) effective start}.
     *
     * <p>This is the clock used by the submission path to record
     * {@code time_from_start}; pairing it with {@link #effectiveEndTime}
     * gives a complete virtual-vs-real timing model.
     */
    public Optional<LocalDateTime> participantClock(ContestParticipant participant, Contest contest) {
        if (contest == null) {
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(participant != null ? participant.getIsVirtual() : null)) {
            return Optional.ofNullable(participant.getStartedAt());
        }
        return effectiveStartTime(contest);
    }

    /**
     * The contest's end time used by the lifecycle scheduler. Prefers
     * the admin-stamped {@code endTime}; falls back to
     * {@code startTime + durationMinutes} when no explicit end has been
     * recorded yet.
     */
    public Optional<LocalDateTime> contestEndTime(Contest contest) {
        if (contest == null) {
            return Optional.empty();
        }
        if (contest.getEndTime() != null) {
            return Optional.of(contest.getEndTime());
        }
        if (contest.getStartTime() != null && contest.getDurationMinutes() != null) {
            return Optional.of(contest.getStartTime().plusMinutes(contest.getDurationMinutes()));
        }
        return Optional.empty();
    }
}
