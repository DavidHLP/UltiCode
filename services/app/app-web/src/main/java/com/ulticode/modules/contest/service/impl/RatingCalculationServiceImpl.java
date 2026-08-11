package com.ulticode.modules.contest.service.impl;

import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.GlobalRanking;
import com.ulticode.modules.contest.entity.enums.RatingTitle;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestRatingCalculationMapper;
import com.ulticode.modules.contest.scoring.ContestRankingComparator;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.contest.service.RatingCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Codeforces-style Elo rating calculation implementation.
 * A durable contest receipt makes the whole calculation retry-safe after a
 * lifecycle finalization failure.
 * Updates global_rankings and contest_participants.final_rank after contest ends.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingCalculationServiceImpl implements RatingCalculationService {

    private final ContestParticipantMapper participantMapper;
    private final GlobalRankingMapper globalRankingMapper;
    /** R6.1 / F-03: needed for the isRated gate (also reused for the F-10
     *  decision record; see ADR-007 §7). */
    private final ContestMapper contestMapper;
    private final ContestRatingCalculationMapper ratingCalculationMapper;

    /** Default Elo rating assigned to participants without a global_ranking record. */
    private static final int DEFAULT_RATING = 1500;

    @Override
    @Transactional
    public void calculateAndUpdate(String contestId) {
        // R6.1 / F-03: isRated controls Elo mutation. Non-rated contests
        // (practice / unranked) still receive final ranks for usable history.
        // F-10 (finishVirtual does NOT trigger recalc) remains unchanged — see
        // ADR-007 §7 for the full decision record.
        Contest contest = contestMapper.selectByIdForUpdate(contestId);
        if (contest == null) {
            log.info("R6.1: contest {} not found, skip rating", contestId);
            return;
        }
        boolean rated = Boolean.TRUE.equals(contest.getIsRated());

        // 1. Fetch all real (non-virtual) participants for this contest.
        //    R3.2 fix: filter by is_virtual = 0 directly, not by status=STARTED.
        //    Once R3.1 closes real participants to FINISHED on contest end, the
        //    old status-based filter would return an empty set and rating would
        //    silently no-op. is_virtual is the true semantic discriminator:
        //    virtual sessions are a per-user replay, not part of the rating
        //    pool, regardless of status.
        List<ContestParticipant> participants = participantMapper.findRealParticipantsByContestId(contestId);
        if (participants.isEmpty()) {
            log.info("No real participants to rate for contest {}", contestId);
            return;
        }

        // Claim the contest calculation in the same transaction as all rating
        // writes. A failure rolls the receipt back; a retry after commit is a
        // no-op and cannot increment contests_attended twice.
        int claimed = ratingCalculationMapper.insertIfAbsent(UUID.randomUUID().toString(), contestId);
        if (claimed == 0) {
            return;
        }

        // 2. Use the same mode and tie-break policy as live ranking.
        //    Wrap in a new ArrayList because the mapper may return an immutable
        //    list; List.sort() requires a mutable copy.
        participants = new java.util.ArrayList<>(participants);
        participants.sort(ContestRankingComparator.forFinal(
                ContestRankingComparator.resolveScoringMode(contest.getScoringMode()),
                ContestRankingComparator.resolveTieBreaker(contest.getTieBreaker())));

        // 3. Assign final_rank (1-based)
        for (int i = 0; i < participants.size(); i++) {
            ContestParticipant p = participants.get(i);
            p.setFinalRank(i + 1);
            participantMapper.updateById(p);
        }

        // Non-rated contests still persist final ranks, but never mutate Elo.
        if (!rated) {
            log.info("R6.1: contest {} isRated=false, final ranks persisted without rating update", contestId);
            return;
        }

        // 4. P1-5 fix: pre-load all opponent ratings in one query (was
        //    O(n^2) single-row SELECTs). Build a userId -> rating map for
        //    the inner Elo loop.
        List<String> userIds = participants.stream()
                .map(ContestParticipant::getUserId)
                .distinct()
                .sorted()
                .toList();
        Map<String, Integer> ratingByUserId = new HashMap<>(userIds.size() * 2);
        for (GlobalRanking gr : globalRankingMapper.findByUserIdsForUpdate(userIds)) {
            ratingByUserId.put(gr.getUserId(),
                    gr.getRating() != null ? gr.getRating() : DEFAULT_RATING);
        }

        // 5. Calculate and update ratings (CF Elo variant)
        //    Only rate participants who have a global_ranking record (D-11).
        for (ContestParticipant participant : participants) {
            String userId = participant.getUserId();
            Integer myRatingBoxed = ratingByUserId.get(userId);
            if (myRatingBoxed == null) {
                // D-11: Skip users without global_ranking record
                log.debug("Skipping rating for user {} -- no global_ranking record", userId);
                continue;
            }
            int oldRating = myRatingBoxed;

            // Compute rating change using CF algorithm against all other participants.
            // HashMap lookups replace per-iteration DB calls.
            int newRating = calculateNewRating(oldRating, ratingByUserId, participants, participant);

            // Determine title from new rating
            RatingTitle newTitle = fromRating(newRating);

            // Update global_ranking
            globalRankingMapper.updateRating(userId, newRating, newTitle.name(), contestId);

            // Update max rating title if new max achieved
            Optional<GlobalRanking> grForMax = globalRankingMapper.findByUserId(userId);
            if (grForMax.isPresent() && newRating > (grForMax.get().getMaxRating() == null ? 0 : grForMax.get().getMaxRating())) {
                globalRankingMapper.updateMaxRatingTitle(newTitle.name(), userId);
            }

            log.debug("User {} rating: {} -> {} (title: {})", userId, oldRating, newRating, newTitle);
        }

        // 6. Recalculate global ranks (global_rank column)
        globalRankingMapper.recalculateGlobalRanks();

        log.info("Rating calculation complete for contest {}: {} participants rated", contestId, participants.size());
    }

    /**
     * Compute new Elo rating for {@code me} using a pre-loaded rating map.
     * Replaces the O(n^2) per-opponent {@code findByUserId} calls with
     * constant-time HashMap lookups. Database roundtrips drop from
     * N*(N-1) to 1 (the pre-load query).
     *
     * <p>Tie handling: per CF convention, opponents at the same final_rank as
     * {@code me} score 0.5 (drawn). P1-5 / quality fix.
     */
    private int calculateNewRating(int myRating,
                                    Map<String, Integer> ratingByUserId,
                                    List<ContestParticipant> allParticipants,
                                    ContestParticipant me) {
        double totalExpected = 0.0;
        double totalActual = 0.0;

        for (ContestParticipant opponent : allParticipants) {
            if (opponent.getUserId().equals(me.getUserId())) continue;

            Integer oppRatingBoxed = ratingByUserId.get(opponent.getUserId());
            if (oppRatingBoxed == null) continue;
            int oppRating = oppRatingBoxed;

            double expected = 1.0 / (1.0 + Math.pow(10, (oppRating - myRating) / 400.0));
            totalExpected += expected;

            // Actual score: 1 if me.placed higher, 0.5 if tied, 0 if lower
            int myRank = me.getFinalRank() != null ? me.getFinalRank() : Integer.MAX_VALUE;
            int oppRank = opponent.getFinalRank() != null ? opponent.getFinalRank() : Integer.MAX_VALUE;
            double actual;
            if (myRank < oppRank) {
                actual = 1.0;
            } else if (myRank == oppRank) {
                actual = 0.5;
            } else {
                actual = 0.0;
            }
            totalActual += actual;
        }

        int opponentCount = allParticipants.size() - 1;
        if (opponentCount <= 0) {
            return myRating;
        }

        int k = determineKFactor(myRating);
        int change = (int) Math.round(k * (totalActual - totalExpected));
        return Math.max(0, Math.min(3500, myRating + change));
    }

    private int determineKFactor(int rating) {
        if (rating < 2100) {
            return 32;
        } else if (rating < 2400) {
            return 24;
        } else {
            return 16;
        }
    }

    static RatingTitle fromRating(int rating) {
        if (rating < 1200) {
            return RatingTitle.NEWBIE;
        } else if (rating < 1400) {
            return RatingTitle.PUPIL;
        } else if (rating < 1600) {
            return RatingTitle.SPECIALIST;
        } else if (rating < 1900) {
            return RatingTitle.EXPERT;
        } else if (rating < 2100) {
            return RatingTitle.CANDIDATE_MASTER;
        } else if (rating < 2300) {
            return RatingTitle.MASTER;
        } else if (rating < 2400) {
            return RatingTitle.INTERNATIONAL_MASTER;
        } else if (rating < 2600) {
            return RatingTitle.GRANDMASTER;
        } else if (rating < 3000) {
            return RatingTitle.INTERNATIONAL_GRANDMASTER;
        } else {
            return RatingTitle.LEGENDARY_GRANDMASTER;
        }
    }
}
