package com.ulticode.modules.achievement.port.adapter;

import com.ulticode.modules.achievement.port.ContestAchievementPort;
import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Legacy adapter implementing the app-api {@link ContestAchievementPort}.
 *
 * <p>Delegates to the legacy {@link AchievementTriggerService} so that
 * backend-app's {@code ContestParticipationServiceImpl} can fire contest
 * participation achievements without importing the achievement module.
 *
 * <p>P7-RELOCATE-CONTEST-001.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyContestAchievementAdapter implements ContestAchievementPort {

    private final AchievementTriggerService achievementTriggerService;

    @Override
    public void triggerContestParticipation(String userId, int participationCount) {
        achievementTriggerService.trigger(userId, AchievementType.CONTEST_PARTICIPATION, participationCount);
    }
}
