package com.ulticode.modules.achievement.service.impl;

import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.criteria.AchievementCriteria;
import com.ulticode.modules.achievement.entity.Achievement;
import com.ulticode.modules.achievement.entity.UserAchievement;
import com.ulticode.modules.achievement.event.AchievementCheckEvent;
import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import com.ulticode.modules.achievement.mapper.UserAchievementMapper;
import com.ulticode.modules.achievement.port.BadgePushPort;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of {@link AchievementTriggerService}.
 *
 * <p>Prior to 2026-07-08 this class carried 11 near-identical
 * <code>onXxx</code> shim methods, one per {@link AchievementType} value.
 * Each shim was a 1-line event publish with a hard-coded enum value. The
 * collapse to a single {@link #trigger(String, AchievementType, int)}
 * method removed ~50 lines of pure pass-through and moved the
 * "which type?" decision to the call site, where it belongs.
 *
 * <p>See ADR (to be filed) and
 * <code>/tmp/architecture-review-1783495648.html</code> candidate 1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementTriggerServiceImpl implements AchievementTriggerService {

    private final Clock clock;
    private final AchievementMapper achievementMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final BadgePushPort badgePushPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Async
    public void trigger(String userId, AchievementType type, int currentValue) {
        eventPublisher.publishEvent(new AchievementCheckEvent(userId, type, currentValue));
    }

    @Override
    @Transactional
    public List<String> checkAndAwardAchievements(String userId, AchievementType type, int currentValue) {
        List<Achievement> allAchievements = achievementMapper.findAllActive();

        List<Achievement> matchingAchievements = allAchievements.stream()
                .filter(a -> AchievementCriteria.from(a.getCriteria()).matches(type))
                .toList();

        List<String> awardedIds = new ArrayList<>();

        // Batch fetch all existing user achievements to avoid N+1 inside loop
        Set<String> earnedAchievementIds = userAchievementMapper.findByUserId(userId)
                .stream()
                .map(UserAchievement::getAchievementId)
                .collect(Collectors.toSet());

        for (Achievement achievement : matchingAchievements) {
            AchievementCriteria criteria = AchievementCriteria.from(achievement.getCriteria());

            if (criteria.isMetBy(currentValue)) {
                if (!earnedAchievementIds.contains(achievement.getId())) {
                    UserAchievement userAchievement = new UserAchievement();
                    userAchievement.setUserId(userId);
                    userAchievement.setAchievementId(achievement.getId());
                    userAchievement.setEarnedAt(LocalDateTime.now(clock));
                    userAchievementMapper.insert(userAchievement);

                    awardedIds.add(achievement.getId());

                    sendBadgeEarnedNotification(userId, achievement);
                    publishAchievementEarnedEvent(userId, achievement);

                    log.info("Awarded achievement {} to user {}", achievement.getKey(), userId);
                }
            }
        }

        return awardedIds;
    }

    private void sendBadgeEarnedNotification(String userId, Achievement achievement) {
        String tierStr = getTierString(achievement.getTier());

        BadgeEarnedPayload payload = BadgeEarnedPayload.of(
                achievement.getId(),
                achievement.getName(),
                achievement.getDescription(),
                achievement.getIcon(),
                tierStr,
                userId);

        badgePushPort.pushBadgeEarned(userId, payload);
    }

    private void publishAchievementEarnedEvent(String userId, Achievement achievement) {
        AchievementEarnedEvent event = AchievementEarnedEvent.of(
                userId,
                achievement.getId(),
                achievement.getKey(),
                achievement.getName(),
                achievement.getDescription(),
                achievement.getIcon(),
                achievement.getTier(),
                achievement.getPoints());

        eventPublisher.publishEvent(event);
    }

    private String getTierString(Integer tier) {
        if (tier == null) {
            return BadgeEarnedPayload.BadgeTier.BRONZE;
        }
        return switch (tier) {
            case 1 -> BadgeEarnedPayload.BadgeTier.BRONZE;
            case 2 -> BadgeEarnedPayload.BadgeTier.SILVER;
            case 3 -> BadgeEarnedPayload.BadgeTier.GOLD;
            case 4 -> BadgeEarnedPayload.BadgeTier.PLATINUM;
            default -> BadgeEarnedPayload.BadgeTier.BRONZE;
        };
    }
}
