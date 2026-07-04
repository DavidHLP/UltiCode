package com.ulticode.modules.achievement.service.impl;

import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.entity.Achievement;
import com.ulticode.modules.achievement.entity.UserAchievement;
import com.ulticode.modules.achievement.event.AchievementCheckEvent;
import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import com.ulticode.modules.achievement.mapper.UserAchievementMapper;
import com.ulticode.modules.achievement.port.BadgePushPort;
import com.ulticode.modules.achievement.service.AchievementService;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of AchievementTriggerService.
 *
 * <p>Trigger methods publish AchievementCheckEvent for async processing.
 * checkAndAwardAchievements runs after transaction commits via AchievementCheckListener.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementTriggerServiceImpl implements AchievementTriggerService {

    private final AchievementMapper achievementMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final AchievementService achievementService;
    private final BadgePushPort badgePushPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Async
    public void onProblemSolved(String userId, int problemsSolvedCount) {
        eventPublisher.publishEvent(new AchievementCheckEvent(userId, AchievementType.PROBLEMS_SOLVED, problemsSolvedCount));
    }

    @Override
    @Async
    public void onSubmissionMade(String userId, int submissionsCount) {
        eventPublisher.publishEvent(new AchievementCheckEvent(userId, AchievementType.SUBMISSIONS_MADE, submissionsCount));
    }

    @Override
    @Async
    public void onContestJoined(String userId, int contestParticipationCount) {
        eventPublisher.publishEvent(new AchievementCheckEvent(userId, AchievementType.CONTEST_PARTICIPATION, contestParticipationCount));
    }

    @Override
    @Async
    public void onContestWon(String userId, int contestWinsCount) {
        eventPublisher.publishEvent(new AchievementCheckEvent(userId, AchievementType.CONTEST_WINS, contestWinsCount));
    }

    @Override
    @Async
    public void onContestPlaced(String userId, int contestPlacedCount) {
        eventPublisher.publishEvent(new AchievementCheckEvent(userId, AchievementType.CONTEST_PLACED, contestPlacedCount));
    }

    @Override
    @Async
    public void onForumPostCreated(String userId, int forumPostsCount) {
        eventPublisher.publishEvent(new AchievementCheckEvent(userId, AchievementType.FORUM_POSTS, forumPostsCount));
    }

    @Override
    @Async
    public void onSolutionWritten(String userId, int solutionsCount) {
        eventPublisher.publishEvent(new AchievementCheckEvent(userId, AchievementType.SOLUTIONS_WRITTEN, solutionsCount));
    }

    @Override
    @Async
    public void onStreakUpdated(String userId, int streakDays) {
        eventPublisher.publishEvent(new AchievementCheckEvent(userId, AchievementType.STREAK_DAYS, streakDays));
    }

    @Override
    @Async
    public void onRatingUpdated(String userId, int rating) {
        eventPublisher.publishEvent(new AchievementCheckEvent(userId, AchievementType.RATING_MILESTONE, rating));
    }

    @Override
    @Async
    public void onFollowCountUpdated(String userId, int followerCount) {
        eventPublisher.publishEvent(new AchievementCheckEvent(userId, AchievementType.FOLLOWER_COUNT, followerCount));
    }

    @Override
    @Async
    public void onFirstProblemSolved(String userId) {
        eventPublisher.publishEvent(new AchievementCheckEvent(userId, AchievementType.FIRST_PROBLEM, 1));
    }

    @Override
    @Async
    public void onLanguageMilestone(String userId, String language, int count) {
        eventPublisher.publishEvent(new AchievementCheckEvent(userId, AchievementType.LANGUAGE_SOLVED, count));
    }

    @Override
    @Transactional
    public List<String> checkAndAwardAchievements(String userId, AchievementType type, int currentValue) {
        List<Achievement> allAchievements = achievementMapper.findAllActive();

        List<Achievement> matchingAchievements = allAchievements.stream()
                .filter(a -> {
                    Map<String, Object> criteria = a.getCriteria();
                    if (criteria == null || !criteria.containsKey("type")) {
                        return false;
                    }
                    String criteriaType = (String) criteria.get("type");
                    return type.getValue().equals(criteriaType);
                })
                .toList();

        List<String> awardedIds = new ArrayList<>();

        // Batch fetch all existing user achievements to avoid N+1 inside loop
        Set<String> earnedAchievementIds = userAchievementMapper.findByUserId(userId)
                .stream()
                .map(UserAchievement::getAchievementId)
                .collect(Collectors.toSet());

        for (Achievement achievement : matchingAchievements) {
            Map<String, Object> criteria = achievement.getCriteria();
            Object targetObj = criteria.get("target");
            int target = 0;
            if (targetObj instanceof Number) {
                target = ((Number) targetObj).intValue();
            }

            if (currentValue >= target) {
                if (!earnedAchievementIds.contains(achievement.getId())) {
                    UserAchievement userAchievement = new UserAchievement();
                    userAchievement.setUserId(userId);
                    userAchievement.setAchievementId(achievement.getId());
                    userAchievement.setEarnedAt(LocalDateTime.now());
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
