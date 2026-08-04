package com.ulticode.modules.achievement.port.adapter;

import com.ulticode.app.api.service.AchievementTriggerPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link AchievementTriggerPort}.
 *
 * <p>P7-RELOCATE left this port without an implementation because the
 * achievement-evaluation logic was not relocated from backend-legacy.
 * The interface contract is fire-and-forget (failures logged, never
 * propagated), so this adapter logs and returns. When achievement
 * evaluation is re-homed, replace this with a delegating adapter.
 */
@Slf4j
@Component
public class AchievementTriggerAdapter implements AchievementTriggerPort {

    @Override
    public void triggerOnSubmission(String userId, Long problemId, boolean accepted,
                                    String submissionId) {
        log.debug("Achievement trigger (no-op legacy stub): user={} problem={} accepted={} submission={}",
                userId, problemId, accepted, submissionId);
    }
}
