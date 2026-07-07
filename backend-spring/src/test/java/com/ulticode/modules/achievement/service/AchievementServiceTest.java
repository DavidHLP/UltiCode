package com.ulticode.modules.achievement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.dto.AchievementDTO;
import com.ulticode.modules.achievement.dto.AchievementVO;
import com.ulticode.modules.achievement.entity.Achievement;
import com.ulticode.modules.achievement.entity.UserAchievement;
import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import com.ulticode.modules.achievement.mapper.UserAchievementMapper;
import com.ulticode.modules.achievement.projection.AchievementProjection;
import com.ulticode.modules.achievement.service.impl.AchievementServiceImpl;
import com.ulticode.modules.achievement.service.impl.AchievementTriggerServiceImpl;
import com.ulticode.modules.achievement.port.BadgePushPort;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the achievement write paths ({@link AchievementServiceImpl})
 * and the trigger service ({@link AchievementTriggerServiceImpl}).
 *
 * <p>Read-path tests (list / getById / getUserAchievements / getUserPoints)
 * were migrated to {@code AchievementProjectionTest} when those methods moved
 * to {@link AchievementProjection} (ADR-0005). The write paths here delegate
 * the post-action view shape to {@code AchievementProjection#toVO}, which is
 * stubbed with a passthrough answer so the assertions still verify the
 * service's own mutation logic.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AchievementServiceTest {

    @Mock
    private AchievementMapper achievementMapper;

    @Mock
    private UserAchievementMapper userAchievementMapper;

    @Mock
    private AchievementProjection achievementProjection;

    @Mock
    private BadgePushPort badgePushPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AchievementService achievementService;

    @Mock
    private Clock clock;

    @InjectMocks
    private AchievementServiceImpl achievementServiceImpl;

    @InjectMocks
    private AchievementTriggerServiceImpl achievementTriggerService;

    private static final String USER_ID = "test-user-id";
    private static final String ACHIEVEMENT_ID = "test-achievement-id";
    private static final String ACHIEVEMENT_KEY = "first_solve";

    private Achievement createTestAchievement() {
        Achievement achievement = new Achievement();
        achievement.setId(ACHIEVEMENT_ID);
        achievement.setKey(ACHIEVEMENT_KEY);
        achievement.setName("First Steps");
        achievement.setDescription("Solve your first problem");
        achievement.setCategory("problem_solving");
        achievement.setTier(1);
        achievement.setPoints(10);
        achievement.setIsActive(true);
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("type", "problems_solved");
        criteria.put("target", 1);
        achievement.setCriteria(criteria);
        return achievement;
    }

    private AchievementDTO createTestAchievementDTO() {
        AchievementDTO dto = new AchievementDTO();
        dto.setKey("new_achievement");
        dto.setName("New Achievement");
        dto.setDescription("A new achievement");
        dto.setCategory("test");
        dto.setTier(1);
        dto.setPoints(50);
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("type", "problems_solved");
        criteria.put("target", 10);
        dto.setCriteria(criteria);
        return dto;
    }

    /**
     * Passthrough stub so write-path tests verify the service's own mutation
     * logic (the entity fields set by create/update) rather than the
     * projection's VO mapping, which has its own dedicated tests.
     */
    private void stubToVOPassthrough() {
        when(achievementProjection.toVO(any(Achievement.class))).thenAnswer(invocation -> {
            Achievement a = invocation.getArgument(0);
            AchievementVO vo = new AchievementVO();
            vo.setId(a.getId());
            vo.setKey(a.getKey());
            vo.setName(a.getName());
            vo.setDescription(a.getDescription());
            vo.setIcon(a.getIcon());
            vo.setCategory(a.getCategory());
            vo.setTier(a.getTier());
            vo.setCriteria(a.getCriteria());
            vo.setPoints(a.getPoints());
            vo.setIsActive(a.getIsActive());
            return vo;
        });
    }

    @Nested
    @DisplayName("Achievement CRUD Tests")
    class AchievementCRUDTests {

        @Test
        @DisplayName("should create achievement successfully")
        void shouldCreateAchievementSuccessfully() {
            AchievementDTO dto = createTestAchievementDTO();
            when(achievementMapper.findByKey(dto.getKey())).thenReturn(null);
            when(achievementMapper.insert(any(Achievement.class))).thenAnswer(invocation -> {
                Achievement a = invocation.getArgument(0);
                a.setId("new-id");
                return 1;
            });
            stubToVOPassthrough();

            AchievementVO result = achievementServiceImpl.create(dto);

            assertNotNull(result);
            assertEquals(dto.getKey(), result.getKey());
            assertEquals(dto.getName(), result.getName());
            verify(achievementMapper).insert(any(Achievement.class));
            verify(achievementProjection).toVO(any(Achievement.class));
        }

        @Test
        @DisplayName("should throw exception when creating achievement with duplicate key")
        void shouldThrowExceptionWhenCreatingWithDuplicateKey() {
            AchievementDTO dto = createTestAchievementDTO();
            Achievement existing = createTestAchievement();
            existing.setKey(dto.getKey());
            when(achievementMapper.findByKey(dto.getKey())).thenReturn(existing);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> achievementServiceImpl.create(dto));
            assertEquals(ErrorCode.CONFLICT.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("should update achievement successfully")
        void shouldUpdateAchievementSuccessfully() {
            Achievement existing = createTestAchievement();
            AchievementDTO dto = new AchievementDTO();
            dto.setName("Updated Name");
            dto.setDescription("Updated description");
            dto.setCategory(existing.getCategory());
            dto.setTier(existing.getTier());
            dto.setCriteria(existing.getCriteria());
            dto.setPoints(existing.getPoints());

            when(achievementMapper.selectById(ACHIEVEMENT_ID)).thenReturn(existing);
            when(achievementMapper.updateById(any(Achievement.class))).thenReturn(1);
            stubToVOPassthrough();

            AchievementVO result = achievementServiceImpl.update(ACHIEVEMENT_ID, dto);

            assertNotNull(result);
            assertEquals("Updated Name", result.getName());
            verify(achievementProjection).toVO(any(Achievement.class));
        }

        @Test
        @DisplayName("should delete achievement and associated user achievements")
        void shouldDeleteAchievementAndUserAchievements() {
            Achievement achievement = createTestAchievement();
            when(achievementMapper.selectById(ACHIEVEMENT_ID)).thenReturn(achievement);
            when(userAchievementMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
            when(achievementMapper.deleteById(ACHIEVEMENT_ID)).thenReturn(1);

            achievementServiceImpl.delete(ACHIEVEMENT_ID);

            verify(userAchievementMapper).delete(any(LambdaQueryWrapper.class));
            verify(achievementMapper).deleteById(ACHIEVEMENT_ID);
        }
    }

    @Nested
    @DisplayName("Achievement Trigger Tests")
    class AchievementTriggerTests {

        @Test
        @DisplayName("should award achievement when target is met")
        void shouldAwardAchievementWhenTargetMet() {
            Achievement achievement = createTestAchievement();
            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(Collections.emptyList());
            when(userAchievementMapper.insert(any(UserAchievement.class))).thenReturn(1);

            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(USER_ID, AchievementType.PROBLEMS_SOLVED, 1);

            assertEquals(1, awarded.size());
            assertEquals(ACHIEVEMENT_ID, awarded.get(0));
            verify(userAchievementMapper).insert(any(UserAchievement.class));
            verify(badgePushPort).pushBadgeEarned(eq(USER_ID), any(BadgeEarnedPayload.class));
            verify(eventPublisher).publishEvent(any(AchievementEarnedEvent.class));
        }

        @Test
        @DisplayName("should not award achievement when target not met")
        void shouldNotAwardAchievementWhenTargetNotMet() {
            Achievement achievement = createTestAchievement();
            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));

            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(USER_ID, AchievementType.PROBLEMS_SOLVED, 0);

            assertTrue(awarded.isEmpty());
            verify(userAchievementMapper, never()).insert(any(UserAchievement.class));
        }

        @Test
        @DisplayName("should not award achievement when already earned")
        void shouldNotAwardAchievementWhenAlreadyEarned() {
            Achievement achievement = createTestAchievement();
            UserAchievement existing = new UserAchievement();
            existing.setAchievementId(ACHIEVEMENT_ID);
            existing.setUserId(USER_ID);

            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(List.of(existing));

            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(USER_ID, AchievementType.PROBLEMS_SOLVED, 1);

            assertTrue(awarded.isEmpty());
            verify(userAchievementMapper, never()).insert(any(UserAchievement.class));
        }

        @Test
        @DisplayName("should check contest participation achievement")
        void shouldCheckContestParticipationAchievement() {
            Achievement achievement = new Achievement();
            achievement.setId("contest-achievement");
            achievement.setKey("first_contest");
            achievement.setName("Competitor");
            achievement.setDescription("Participate in your first contest");
            achievement.setCategory("contest");
            achievement.setTier(1);
            achievement.setPoints(25);
            achievement.setIsActive(true);
            Map<String, Object> criteria = new HashMap<>();
            criteria.put("type", "contest_participation");
            criteria.put("target", 1);
            achievement.setCriteria(criteria);

            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(Collections.emptyList());
            when(userAchievementMapper.insert(any(UserAchievement.class))).thenReturn(1);

            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(USER_ID, AchievementType.CONTEST_PARTICIPATION, 1);

            assertEquals(1, awarded.size());
        }

        @Test
        @DisplayName("should check solution written achievement")
        void shouldCheckSolutionWrittenAchievement() {
            Achievement achievement = new Achievement();
            achievement.setId("solution-achievement");
            achievement.setKey("first_solution");
            achievement.setName("Helper");
            achievement.setDescription("Write your first solution");
            achievement.setCategory("community");
            achievement.setTier(1);
            achievement.setPoints(15);
            achievement.setIsActive(true);
            Map<String, Object> criteria = new HashMap<>();
            criteria.put("type", "solutions_written");
            criteria.put("target", 1);
            achievement.setCriteria(criteria);

            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(Collections.emptyList());
            when(userAchievementMapper.insert(any(UserAchievement.class))).thenReturn(1);

            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(USER_ID, AchievementType.SOLUTIONS_WRITTEN, 1);

            assertEquals(1, awarded.size());
        }

        @Test
        @DisplayName("should check streak achievement")
        void shouldCheckStreakAchievement() {
            Achievement achievement = new Achievement();
            achievement.setId("streak-achievement");
            achievement.setKey("streak_7");
            achievement.setName("Week Warrior");
            achievement.setDescription("Maintain a 7-day streak");
            achievement.setCategory("consistency");
            achievement.setTier(1);
            achievement.setPoints(50);
            achievement.setIsActive(true);
            Map<String, Object> criteria = new HashMap<>();
            criteria.put("type", "streak_days");
            criteria.put("target", 7);
            achievement.setCriteria(criteria);

            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(Collections.emptyList());
            when(userAchievementMapper.insert(any(UserAchievement.class))).thenReturn(1);

            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(USER_ID, AchievementType.STREAK_DAYS, 7);

            assertEquals(1, awarded.size());
        }

        @Test
        @DisplayName("should check generic achievement type")
        void shouldCheckGenericAchievementType() {
            Achievement achievement = new Achievement();
            achievement.setId("rating-achievement");
            achievement.setKey("rating_1500");
            achievement.setName("Rising Star");
            achievement.setDescription("Reach 1500 rating");
            achievement.setCategory("rating");
            achievement.setTier(2);
            achievement.setPoints(100);
            achievement.setIsActive(true);
            Map<String, Object> criteria = new HashMap<>();
            criteria.put("type", "rating_milestone");
            criteria.put("target", 1500);
            achievement.setCriteria(criteria);

            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(Collections.emptyList());
            when(userAchievementMapper.insert(any(UserAchievement.class))).thenReturn(1);

            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(
                    USER_ID, AchievementType.RATING_MILESTONE, 1500);

            assertEquals(1, awarded.size());
        }
    }

    @Nested
    @DisplayName("AchievementType Tests")
    class AchievementTypeTests {

        @Test
        @DisplayName("should parse achievement type from value")
        void shouldParseAchievementTypeFromValue() {
            assertEquals(AchievementType.PROBLEMS_SOLVED, AchievementType.fromValue("problems_solved"));
            assertEquals(AchievementType.SUBMISSIONS_MADE, AchievementType.fromValue("submissions_made"));
            assertEquals(AchievementType.CONTEST_PARTICIPATION, AchievementType.fromValue("contest_participation"));
            assertNull(AchievementType.fromValue("invalid"));
            assertNull(AchievementType.fromValue(null));
        }

        @Test
        @DisplayName("should return correct value for achievement type")
        void shouldReturnCorrectValueForAchievementType() {
            assertEquals("problems_solved", AchievementType.PROBLEMS_SOLVED.getValue());
            assertEquals("contest_wins", AchievementType.CONTEST_WINS.getValue());
            assertEquals("community_contributor", AchievementType.COMMUNITY_CONTRIBUTOR.getValue());
        }
    }
}
