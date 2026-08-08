package com.ulticode.modules.contest.service;

import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Single seam for every {@link com.ulticode.modules.contest.entity.ContestParticipant}
 * status transition. Both the scheduled lifecycle path
 * ({@link ContestLifecycleService}) and the interactive participation path
 * ({@link ContestParticipationService}) delegate here so conditional-UPDATE
 * guards, clock arithmetic, input hygiene, and the canonical status literals
 * concentrate in one deep module instead of being mirrored as pass-throughs
 * on each caller.
 *
 * <p>Atomicity contract: every method that mutates a status does so through
 * the underlying {@link com.ulticode.modules.contest.mapper.ContestParticipantMapper}
 * SQL guard. The guard itself is a single conditional UPDATE in the mapper —
 * not in this service — because the SQL must hold against scheduler / rejudge
 * races that bypass the service layer entirely. This interface owns the
 * <em>seam</em> the service layer crosses; the SQL owns the <em>atomicity</em>.
 *
 * <p>Read vs write ownership: read-then-write composites
 * (e.g. {@link #findAndFinishExpiredVirtuals}) live here so the participant
 * mapper never leaks back into the lifecycle module. Single-row reads that
 * have no transition policy stay on the mapper (called directly by their
 * owner) because exposing every read on the seam would make the seam
 * broader than its purpose.
 *
 * <p>Ownership of invariants:
 * <ul>
 *   <li>D-05 / D-06 — status rules enforced as conditional-UPDATE guards in the mapper SQL.</li>
 *   <li>R6.2 / F-06 — effective time arithmetic stays in
 *       {@link com.ulticode.modules.contest.clock.ContestClock}.</li>
 *   <li>D-04 — submission intake is not modified by this module.</li>
 *   <li>Input hygiene — the bulk virtual-finish method dedups ids and rejects
 *       null / empty collections; the start method rejects negative
 *       capacities, etc. See individual method Javadoc for the exact rules.</li>
 * </ul>
 */
public interface ContestParticipantTransitions {

    /**
     * Bulk-transition every REGISTERED participant of a contest to STARTED,
     * stamping {@code started_at} to {@code now}. Used by the lifecycle tick
     * when a contest transitions UPCOMING → RUNNING.
     *
     * <p>Atomicity: the mapper SQL guards on {@code status = 'REGISTERED'}, so
     * already-STARTED participants are not re-stamped.
     *
     * @param contestId the contest whose participants to start
     * @param now       current timestamp
     * @return number of rows transitioned
     */
    int batchStartRegistered(String contestId, LocalDateTime now);

    /**
     * Bulk-transition all STARTED real (non-virtual) participants of a contest
     * to FINISHED, stamping {@code finished_at} to {@code now}. Used by the
     * lifecycle tick when a contest transitions RUNNING → FINISHED.
     *
     * <p>Atomicity: the mapper SQL guards on
     * {@code status = 'STARTED' AND is_virtual = 0}, so virtual participants
     * and already-FINISHED rows are not touched.
     *
     * @param contestId the contest whose real participants to close
     * @param now       current timestamp
     * @return number of rows transitioned
     */
    int finishStartedReal(String contestId, LocalDateTime now);

    /**
     * Bulk-transition a set of virtual participant ids to FINISHED, stamping
     * {@code finished_at} to {@code now}. Used by the lifecycle tick for
     * expired virtual sessions and by interactive {@code finishVirtualContest}
     * for user-initiated completion (with a single-element id list).
     *
     * <p>Atomicity: the mapper SQL guards on
     * {@code status = 'STARTED' AND is_virtual = 1}, so already-FINISHED rows
     * and real participants are not touched. Concurrent calls serialise
     * through the per-row {@code WHERE id IN ...} guard — a second call
     * targeting the same id becomes a no-op (affected rows = 0).
     *
     * <p>Input hygiene: a {@code null} or empty collection is treated as a
     * no-op (returns 0) so callers don't have to short-circuit before
     * invoking. Duplicate ids are deduped before the SQL round-trip.
     *
     * @param participantIds the participant row ids
     * @param now            current timestamp
     * @return number of rows transitioned
     */
    int bulkFinishVirtualByIds(Collection<String> participantIds, LocalDateTime now);

    /**
     * Read-then-write composite: find every virtual participant whose
     * {@code started_at + duration_minutes} has elapsed, then atomically
     * transition them to FINISHED.
     *
     * <p>This is the single seam entry for the scheduler's auto-finish path
     * so the lifecycle module never has to query the participant mapper
     * directly. The seam owns both the SELECT (capped by
     * {@link ContestClock}-derived arithmetic) and the bulk UPDATE.
     *
     * @param now the wall-clock instant the scheduler observed
     * @return number of rows transitioned
     */
    int findAndFinishExpiredVirtuals(LocalDateTime now);

    /**
     * Persist a freshly-built real (non-virtual) participant in the REGISTERED
     * state. The mapper's base {@code insert} performs the DB write; the
     * DB unique key {@code contest_id, user_id, virtual_session_id} is the
     * source of truth for race detection.
     *
     * <p>This is a <em>register</em> operation, not a transition between
     * existing states, but it lives on the same seam so the canonical
     * status literal and the side-effect ordering stay in one place.
     *
     * @param participant the participant row to insert; must have
     *                    {@code isVirtual = false} and
     *                    {@code status = REGISTERED}
     * @return the same instance, with any DB-assigned fields populated
     *         (currently a pass-through; kept for API symmetry)
     */
    ContestParticipant registerRealParticipant(ContestParticipant participant);

    /**
     * Persist a freshly-built virtual (replay) participant in the STARTED
     * state. The mapper's base {@code insert} performs the DB write; the
     * DB unique key {@code uk_virtual_active} (V20260720120000) on the
     * {@code active_virtual_key} generated column prevents concurrent
     * active virtual sessions for the same user in the same contest.
     *
     * <p>This is a <em>start virtual session</em> operation, not a transition
     * between existing states, but it lives on the same seam so the
     * canonical status literal and the side-effect ordering stay in one
     * place.
     *
     * @param participant the participant row to insert; must have
     *                    {@code isVirtual = true},
     *                    {@code status = STARTED}, and a non-null
     *                    {@code virtualSessionId}
     * @return the same instance, with any DB-assigned fields populated
     */
    ContestParticipant startVirtualParticipant(ContestParticipant participant);

    /**
     * Delete a real (non-virtual) participant row by id. Used by the
     * unregister path. The DB unique key handles the inverse case (already
     * deleted → 0 rows affected, treated as a no-op).
     *
     * @param participantId the id of the participant to delete
     * @return number of rows affected (0 or 1)
     */
    int deleteById(String participantId);

    /**
     * Cascade-delete every participant row for a contest. Used by the
     * soft-delete cascade path. Lives on the seam (not directly on the
     * mapper) so the lifecycle module's only collaborator for
     * {@code contest_participants} is this interface; the mapper stays an
     * implementation detail of the seam.
     *
     * @param contestId the contest whose participant rows to delete
     * @return number of rows affected
     */
    int deleteAllByContestId(String contestId);

    /**
     * Read-only lookup used by the reminder fan-out. Lives on the seam
     * because the only caller (the reminder tick) co-locates the read
     * with the dispatch loop; exposing it here avoids pulling the
     * participant mapper back into the lifecycle module for a single
     * SELECT.
     *
     * @param contestIds non-empty list of contest ids
     * @return list of participants for those contests
     */
    List<ContestParticipant> findByContestIdsForReminder(List<String> contestIds);

    /**
     * Canonical {@code REGISTERED} status literal. Exposed so callers that
     * need to set a freshly-built participant to the registered state can
     * use the enum / wire value without a second import.
     */
    ContestParticipantStatus REGISTERED = ContestParticipantStatus.REGISTERED;

    /**
     * Canonical {@code STARTED} status literal.
     */
    ContestParticipantStatus STARTED = ContestParticipantStatus.STARTED;

    /**
     * Canonical {@code FINISHED} status literal.
     */
    ContestParticipantStatus FINISHED = ContestParticipantStatus.FINISHED;

    /**
     * Canonical {@code DISQUALIFIED} status literal. Currently reserved
     * for the admin disqualification path; no production caller has shipped
     * it yet, but the enum value is wired so a future admin disqualify
     * endpoint can land without a migration.
     */
    ContestParticipantStatus DISQUALIFIED = ContestParticipantStatus.DISQUALIFIED;
}
