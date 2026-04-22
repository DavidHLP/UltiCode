package com.ulticode.modules.achievement.listener;

import com.ulticode.modules.achievement.event.AchievementCheckEvent;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementCheckListener {

    private final AchievementTriggerService achievementTriggerService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAchievementCheck(AchievementCheckEvent event) {
        try {
            achievementTriggerService.checkAndAwardAchievements(
                    event.userId(),
                    event.type(),
                    event.currentValue()
            );
        } catch (Exception e) {
            log.warn("Failed to check/award achievements for user {} type {}: {}",
                    event.userId(), event.type(), e.getMessage());
        }
    }
}
