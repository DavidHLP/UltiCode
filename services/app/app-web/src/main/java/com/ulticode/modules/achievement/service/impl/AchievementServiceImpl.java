package com.ulticode.modules.achievement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.AchievementErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.modules.achievement.dto.AchievementDTO;
import com.ulticode.modules.achievement.dto.AchievementVO;
import com.ulticode.modules.achievement.entity.Achievement;
import com.ulticode.modules.achievement.entity.UserAchievement;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import com.ulticode.modules.achievement.mapper.UserAchievementMapper;
import com.ulticode.modules.achievement.projection.AchievementProjection;
import com.ulticode.modules.achievement.service.AchievementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-path implementation for achievement CRUD.
 *
 * <p>Read paths were extracted into {@link AchievementProjection} (ADR-0005).
 * This impl now injects only the two achievement-side mappers it actually
 * uses; read-only submission counters moved with the read paths to the
 * projection, and the unused {@code ContestParticipantMapper} field was
 * dropped entirely. Post-action view shapes are produced via
 * {@link AchievementProjection#toVO(Achievement)} &mdash; mirroring the
 * ModerationProjection {@code toAppealVO} pattern (ADR-0004).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementServiceImpl implements AchievementService {

    private final AchievementMapper achievementMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final AchievementProjection achievementProjection;

    @Override
    @Transactional
    public AchievementVO create(AchievementDTO dto) {
        // Check if key already exists
        Achievement existing = achievementMapper.findByKey(dto.getKey());
        if (existing != null) {
            throw new BusinessException(BaseErrorCode.CONFLICT, "Achievement with key '" + dto.getKey() + "' already exists");
        }

        Achievement achievement = new Achievement();
        achievement.setKey(dto.getKey());
        achievement.setName(dto.getName());
        achievement.setDescription(dto.getDescription());
        achievement.setIcon(dto.getIcon());
        achievement.setCategory(dto.getCategory());
        achievement.setTier(dto.getTier());
        achievement.setCriteria(dto.getCriteria());
        achievement.setPoints(dto.getPoints());
        achievement.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);

        achievementMapper.insert(achievement);
        log.info("Created achievement: {} with key: {}", achievement.getName(), achievement.getKey());

        return achievementProjection.toVO(achievement);
    }

    @Override
    @Transactional
    public AchievementVO update(String id, AchievementDTO dto) {
        Achievement achievement = achievementMapper.selectById(id);
        if (achievement == null) {
            throw new BusinessException(AchievementErrorCode.ACHIEVEMENT_NOT_FOUND, "Achievement not found");
        }

        // Check key uniqueness if changing
        if (dto.getKey() != null && !dto.getKey().equals(achievement.getKey())) {
            Achievement existing = achievementMapper.findByKey(dto.getKey());
            if (existing != null) {
                throw new BusinessException(BaseErrorCode.CONFLICT, "Achievement with key '" + dto.getKey() + "' already exists");
            }
            achievement.setKey(dto.getKey());
        }

        if (dto.getName() != null) {
            achievement.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            achievement.setDescription(dto.getDescription());
        }
        if (dto.getIcon() != null) {
            achievement.setIcon(dto.getIcon());
        }
        if (dto.getCategory() != null) {
            achievement.setCategory(dto.getCategory());
        }
        if (dto.getTier() != null) {
            achievement.setTier(dto.getTier());
        }
        if (dto.getCriteria() != null) {
            achievement.setCriteria(dto.getCriteria());
        }
        if (dto.getPoints() != null) {
            achievement.setPoints(dto.getPoints());
        }
        if (dto.getIsActive() != null) {
            achievement.setIsActive(dto.getIsActive());
        }

        achievementMapper.updateById(achievement);
        log.info("Updated achievement: {}", achievement.getName());

        return achievementProjection.toVO(achievement);
    }

    @Override
    @Transactional
    public void delete(String id) {
        Achievement achievement = achievementMapper.selectById(id);
        if (achievement == null) {
            throw new BusinessException(AchievementErrorCode.ACHIEVEMENT_NOT_FOUND, "Achievement not found");
        }

        // Delete associated user achievements
        LambdaQueryWrapper<UserAchievement> uaWrapper = new LambdaQueryWrapper<>();
        uaWrapper.eq(UserAchievement::getAchievementId, id);
        userAchievementMapper.delete(uaWrapper);

        achievementMapper.deleteById(id);
        log.info("Deleted achievement: {}", achievement.getName());
    }

    @Override
    public Achievement findByKey(String key) {
        return achievementMapper.findByKey(key);
    }
}
