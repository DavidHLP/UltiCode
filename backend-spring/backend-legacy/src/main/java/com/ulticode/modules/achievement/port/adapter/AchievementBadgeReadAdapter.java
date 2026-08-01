package com.ulticode.modules.achievement.port.adapter;

import com.ulticode.modules.achievement.entity.Achievement;
import com.ulticode.modules.achievement.entity.UserAchievement;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import com.ulticode.modules.achievement.mapper.UserAchievementMapper;
import com.ulticode.app.api.service.AchievementBadgeReadPort;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Default adapter for {@link AchievementBadgeReadPort}. Lives in the
 * {@code achievement} module so the {@code solution} cluster never
 * imports {@code UserAchievementMapper} or {@code AchievementMapper}.
 *
 * @author ulticode
 */
@Component
public class AchievementBadgeReadAdapter implements AchievementBadgeReadPort {

    private final UserAchievementMapper userAchievementMapper;
    private final AchievementMapper achievementMapper;

    public AchievementBadgeReadAdapter(UserAchievementMapper userAchievementMapper,
                                       AchievementMapper achievementMapper) {
        this.userAchievementMapper = userAchievementMapper;
        this.achievementMapper = achievementMapper;
    }

    @Override
    public List<String> findBadgeNames(String userId, int limit) {
        if (userId == null || limit <= 0) return Collections.emptyList();
        List<UserAchievement> rows = userAchievementMapper.findByUserId(userId);
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        return rows.stream()
                .map(ua -> {
                    Achievement a = achievementMapper.selectById(ua.getAchievementId());
                    return a == null ? null : a.getName();
                })
                .filter(Objects::nonNull)
                .limit(limit)
                .collect(Collectors.toList());
    }
}