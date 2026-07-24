package com.ulticode.modules.contest.service.impl;

import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.service.ContestParticipantTransitions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single owner of {@link ContestParticipant} status transitions and
 * register / start / delete operations. Every call from the scheduled
 * lifecycle path and the interactive participation path crosses this
 * implementation, so the canonical status literals, input hygiene
 * (null / empty guard, dedup, hashset copy), and the atomic SQL guard
 * boundary concentrate in one deep module.
 *
 * <p>The {@code @Transactional} annotation is deliberately placed on the
 * individual methods (not the class) so callers that compose multiple
 * transition calls into a single business transaction — e.g. the
 * unregister path that decrements the contest's registered counter and
 * then deletes the row — can drive this seam from within their own
 * outer transaction without nested-transaction surprises.
 *
 * <p>Atomicity: every write method delegates to the mapper, which performs
 * the write as a single conditional UPDATE / INSERT. The status literal
 * passed to the mapper is {@link ContestParticipantStatus#wireValue()},
 * so the SQL guard and the column are compared with the same durable
 * value. Read-then-write composites ({@link #findAndFinishExpiredVirtuals})
 * keep both halves on this seam so callers do not have to coordinate a
 * mapper query with a seam write.
 */
@Service
@RequiredArgsConstructor
public class ContestParticipantTransitionsImpl implements ContestParticipantTransitions {

    private final ContestParticipantMapper participantMapper;

    @Override
    @Transactional
    public int batchStartRegistered(String contestId, LocalDateTime now) {
        // Atomic SQL guard: only REGISTERED → STARTED rows are touched. The
        // mapper's COALESCE on started_at preserves the original timestamp if
        // a row was already STARTED for any reason (defensive: should never
        // happen because the WHERE clause excludes it, but matches the
        // pre-removal semantics for safety).
        return participantMapper.startRegisteredParticipants(contestId, now);
    }

    @Override
    @Transactional
    public int finishStartedReal(String contestId, LocalDateTime now) {
        // Real participants: is_virtual = 0. We do NOT clear virtual_session_id
        // because real participants never have one; preserving the column
        // keeps the row indistinguishable from a freshly-inserted real one.
        return participantMapper.finishStartedRealParticipants(contestId, now);
    }

    @Override
    @Transactional
    public int bulkFinishVirtualByIds(Collection<String> participantIds, LocalDateTime now) {
        // Input hygiene: null or empty → 0 affected rows. Dedup into a HashSet
        // before the SQL round-trip so callers can pass any Collection shape
        // (e.g. a list returned by findVirtualParticipantsToFinish) without
        // paying for accidental duplicate IN-clause expansion.
        if (participantIds == null || participantIds.isEmpty()) {
            return 0;
        }
        Set<String> deduped = new HashSet<>(participantIds);
        // Atomic SQL guard: only STARTED + is_virtual = 1 rows are touched.
        return participantMapper.finishStartedVirtualParticipantsByIds(deduped, now);
    }

    @Override
    @Transactional
    public int findAndFinishExpiredVirtuals(LocalDateTime now) {
        // Read-then-write composite kept on the seam so the lifecycle module
        // never has to query the participant mapper directly. The SELECT
        // filters by started_at + duration_minutes (see mapper SQL); the
        // bulkFinishVirtualByIds method applies the dedup + atomic guard.
        List<ContestParticipant> toFinish = participantMapper.findVirtualParticipantsToFinish(now);
        if (toFinish.isEmpty()) {
            return 0;
        }
        Set<String> ids = new HashSet<>();
        for (ContestParticipant p : toFinish) {
            ids.add(p.getId());
        }
        return bulkFinishVirtualByIds(ids, now);
    }

    @Override
    @Transactional
    public ContestParticipant registerRealParticipant(ContestParticipant participant) {
        // Defensive normalisation: force the canonical status literal so a
        // caller that built a participant with the wrong status name still
        // hits the DB unique key with the registered value. The mapper
        // performs the INSERT; the DB unique key on
        // (contest_id, user_id, virtual_session_id) is the source of truth
        // for duplicate detection, and the caller catches the
        // DuplicateKeyException to map it to the application-level
        // CONTEST_ALREADY_REGISTERED error.
        participant.setStatusEnum(REGISTERED);
        participant.setIsVirtual(false);
        participantMapper.insert(participant);
        return participant;
    }

    @Override
    @Transactional
    public ContestParticipant startVirtualParticipant(ContestParticipant participant) {
        // Defensive normalisation: force the canonical status literal and
        // isVirtual flag so the partial-unique index
        // (V20260720120000__Add_Uk_Virtual_Active_Constraint.sql) computes
        // active_virtual_key correctly. The DB unique key
        // (uk_virtual_active) detects concurrent active virtual sessions
        // and the caller catches the DuplicateKeyException to short-circuit
        // to the existing winner row.
        participant.setStatusEnum(STARTED);
        participant.setIsVirtual(true);
        participantMapper.insert(participant);
        return participant;
    }

    @Override
    @Transactional
    public int deleteById(String participantId) {
        // MyBatis-Plus base deleteById returns the affected-row count. 0 means
        // the row was already deleted (idempotent) or never existed; the
        // unregister path treats 0 as a no-op.
        return participantMapper.deleteById(participantId);
    }

    @Override
    @Transactional
    public int deleteAllByContestId(String contestId) {
        // Bulk cascade delete does not need input hygiene (contestId is
        // validated upstream by ContestServiceImpl) and it never touches
        // the status column, so the seam is just a thin pass-through to
        // the mapper. Kept on the seam so the lifecycle module has a
        // single collaborator for contest_participants.
        return participantMapper.deleteByContestId(contestId);
    }

    @Override
    public List<ContestParticipant> findByContestIdsForReminder(List<String> contestIds) {
        // Read-only path. Kept off the @Transactional methods (no write
        // happens) so the read does not open a transaction. The mapper
        // returns rows in (contest_id, registered_at) order which is
        // what the reminder fan-out expects.
        return participantMapper.findByContestIds(contestIds);
    }
}
