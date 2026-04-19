package com.ulticode.modules.contest.service.impl;

import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.GlobalRanking;
import com.ulticode.modules.contest.entity.enums.RatingTitle;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.contest.service.RatingCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Codeforces-style Elo rating calculation implementation.
 * Updates global_rankings and contest_participants.final_rank after contest ends.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingCalculationServiceImpl implements RatingCalculationService {

    private final ContestParticipantMapper participantMapper;
    private final GlobalRankingMapper globalRankingMapper;

    @Override
    @Transactional
    public void calculateAndUpdate(String contestId) {
        // 1. Fetch all STARTED participants for this contest
        List<ContestParticipant> participants = participantMapper.findByContestIdAndStatus(
                contestId, "STARTED");
        if (participants.isEmpty()) {
            log.info("No participants to rate for contest {}", contestId);
            return;
        }

        // 2. Sort by score (DESC) then penalty (ASC) to determine rank
        participants.sort((a, b) -> {
            int scoreCmp = Double.compare(
                    b.getTotalScore() != null ? b.getTotalScore() : 0,
                    a.getTotalScore() != null ? a.getTotalScore() : 0);
            if (scoreCmp != 0) return scoreCmp;
            return Integer.compare(
                    a.getTotalPenalty() != null ? a.getTotalPenalty() : 0,
                    b.getTotalPenalty() != null ? b.getTotalPenalty() : 0);
        });

        // 3. Assign final_rank (1-based)
        for (int i = 0; i < participants.size(); i++) {
            ContestParticipant p = participants.get(i);
            p.setFinalRank(i + 1);
            participantMapper.updateById(p);
        }

        // 4. Calculate and update ratings (CF Elo variant)
        // Only rate participants who have a global_ranking record (D-11)
        for (ContestParticipant participant : participants) {
            String userId = participant.getUserId();
            Optional<GlobalRanking> grOpt = globalRankingMapper.findByUserId(userId);
            if (grOpt.isEmpty()) {
                // D-11: Skip users without global_ranking record
                log.debug("Skipping rating for user {} -- no global_ranking record", userId);
                continue;
            }
            GlobalRanking gr = grOpt.get();
            int oldRating = gr.getRating() != null ? gr.getRating() : 1500;

            // Compute rating change using CF algorithm against all other participants
            int newRating = calculateNewRating(oldRating, participants, participant);

            // Determine title from new rating
            RatingTitle newTitle = fromRating(newRating);

            // Update global_ranking
            globalRankingMapper.updateRating(userId, newRating, newTitle.name(), contestId);

            // Update max rating title if new max achieved
            if (newRating > gr.getMaxRating()) {
                globalRankingMapper.updateMaxRatingTitle(newTitle.name(), userId);
            }

            log.debug("User {} rating: {} -> {} (title: {})", userId, oldRating, newRating, newTitle);
        }

        // 5. Recalculate global ranks (global_rank column)
        globalRankingMapper.recalculateGlobalRanks();

        log.info("Rating calculation complete for contest {}: {} participants rated", contestId, participants.size());
    }

    private int calculateNewRating(int myRating, List<ContestParticipant> allParticipants,
                                    ContestParticipant me) {
        double totalExpected = 0.0;
        double totalActual = 0.0;

        for (ContestParticipant opponent : allParticipants) {
            if (opponent.getUserId().equals(me.getUserId())) continue;

            Optional<GlobalRanking> oppGr = globalRankingMapper.findByUserId(opponent.getUserId());
            if (oppGr.isEmpty()) continue;

            int oppRating = oppGr.get().getRating() != null ? oppGr.get().getRating() : 1500;
            double expected = 1.0 / (1.0 + Math.pow(10, (oppRating - myRating) / 400.0));
            totalExpected += expected;

            // Actual score: 1 if me.rank < opponent.rank (placed higher), 0 otherwise
            int myRank = me.getFinalRank() != null ? me.getFinalRank() : Integer.MAX_VALUE;
            int oppRank = opponent.getFinalRank() != null ? opponent.getFinalRank() : Integer.MAX_VALUE;
            double actual = myRank < oppRank ? 1.0 : 0.0;
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
