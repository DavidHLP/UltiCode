package com.ulticode.modules.achievement.service.impl;

import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.dto.AchievementProgressDTO;
import com.ulticode.modules.achievement.entity.Achievement;
import com.ulticode.modules.achievement.entity.UserAchievement;
import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import com.ulticode.modules.achievement.mapper.UserAchievementMapper;
import com.ulticode.modules.achievement.service.AchievementService;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import com.ulticode.modules.websocket.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of AchievementTriggerService.
 *
 * <p>This service is responsible for checking and awarding achievements
 * when users perform certain actions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementTriggerServiceImpl implements AchievementTriggerService {

    private final AchievementMapper achievementMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final AchievementService achievementService;
    private final RealtimeService realtimeService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<String> onProblemSolved(String userId, int problemsSolvedCount) {
        return checkAndAwardAchievements(userId, AchievementType.PROBLEMS_SOLVED, problemsSolvedCount);
    }

    @Override
    public List<String> onSubmissionMade(String userId, int submissionsCount) {
        return checkAndAwardAchievements(userId, AchievementType.SUBMISSIONS_MADE, submissionsCount);
    }

    @Override
    public List<String> onContestJoined(String userId, int contestParticipationCount) {
        return checkAndAwardAchievements(userId, AchievementType.CONTEST_PARTICIPATION, contestParticipationCount);
    }

    @Override
    public List<String> onContestWon(String userId, int contestWinsCount) {
        return checkAndAwardAchievements(userId, AchievementType.CONTEST_WINS, contestWinsCount);
    }

    @Override
    public List<String> onContestPlaced(String userId, int contestPlacedCount) {
        return checkAndAwardAchievements(userId, AchievementType.CONTEST_PLACED, contestPlacedCount);
    }

    @Override
    public List<String> onForumPostCreated(String userId, int forumPostsCount) {
        return checkAndAwardAchievements(userId, AchievementType.FORUM_POSTS, forumPostsCount);
    }

    @Override
    public List<String> onSolutionWritten(String userId, int solutionsCount) {
        return checkAndAwardAchievements(userId, AchievementType.SOLUTIONS_WRITTEN, solutionsCount);
    }

    @Override
    public List<String> onStreakUpdated(String userId, int streakDays) {
        return checkAndAwardAchievements(userId, AchievementType.STREAK_DAYS, streakDays);
    }

    @Override
    public List<String> onRatingUpdated(String userId, int rating) {
        return checkAndAwardAchievements(userId, AchievementType.RATING_MILESTONE, rating);
    }

    @Override
    public List<String> onFollowCountUpdated(String userId, int followerCount) {
        return checkAndAwardAchievements(userId, AchievementType.FOLLOWER_COUNT, followerCount);
    }

    @Override
    public List<String> onFirstProblemSolved(String userId) {
        return checkAndAwardAchievements(userId, AchievementType.FIRST_PROBLEM, 1);
    }

    @Override
    public List<String> onLanguageMilestone(String userId, String language, int count) {
        return checkAndAwardAchievements(userId, AchievementType.LANGUAGE_SOLVED, count);
    }

    @Override
    @Transactional
    public List<String> checkAndAwardAchievements(String userId, AchievementType type, int currentValue) {
        List<Achievement> allAchievements = achievementMapper.findAllActive();

        // Filter achievements matching the criteria type
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

        for (Achievement achievement : matchingAchievements) {
            Map<String, Object> criteria = achievement.getCriteria();
            Object targetObj = criteria.get("target");
            int target = 0;
            if (targetObj instanceof Number) {
                target = ((Number) targetObj).intValue();
            }

            if (currentValue >= target) {
                // Check if already earned
                UserAchievement existing = userAchievementMapper.findByUserAndAchievement(
                        userId, achievement.getId());

                if (existing == null) {
                    // Award the achievement
                    UserAchievement userAchievement = new UserAchievement();
                    userAchievement.setUserId(userId);
                    userAchievement.setAchievementId(achievement.getId());
                    userAchievement.setEarnedAt(LocalDateTime.now());
                    userAchievementMapper.insert(userAchievement);

                    awardedIds.add(achievement.getId());

                    // Send real-time notification via WebSocket
                    sendBadgeEarnedNotification(userId, achievement);

                    // Publish event
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

        realtimeService.sendNotification(userId, payload);
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
