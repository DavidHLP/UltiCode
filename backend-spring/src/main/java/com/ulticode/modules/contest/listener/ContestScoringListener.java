package com.ulticode.modules.contest.listener;

import com.ulticode.modules.contest.service.ContestScoringService;
import com.ulticode.modules.submission.event.SubmissionJudgedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the submission module's {@link SubmissionJudgedEvent} (published after the
 * source transaction commits) to the contest module's {@link ContestScoringService}.
 *
 * <p>The {@code AFTER_COMMIT} phase guarantees we never read uncommitted submission
 * state. All exceptions are caught and logged so a contest-scoring failure cannot
 * propagate back to the judge worker (which has already moved on).
 *
 * <p>This mirrors the pattern used by
 * {@code com.ulticode.modules.achievement.listener.AchievementCheckListener}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContestScoringListener {

    private final ContestScoringService contestScoringService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionJudged(SubmissionJudgedEvent event) {
        try {
            contestScoringService.applyJudgeResult(event);
        } catch (Exception e) {
            // Swallow + log: a scoring failure must not break the judge pipeline.
            log.warn("Failed to apply contest scoring for submission {}: {}",
                    event.getSubmissionId(), e.getMessage(), e);
        }
    }
}
