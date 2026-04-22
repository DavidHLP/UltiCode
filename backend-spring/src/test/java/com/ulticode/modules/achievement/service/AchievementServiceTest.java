package com.ulticode.modules.achievement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.dto.*;
import com.ulticode.modules.achievement.entity.Achievement;
import com.ulticode.modules.achievement.entity.UserAchievement;
import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import com.ulticode.modules.achievement.mapper.UserAchievementMapper;
import com.ulticode.modules.achievement.service.impl.AchievementServiceImpl;
import com.ulticode.modules.achievement.service.impl.AchievementTriggerServiceImpl;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import com.ulticode.modules.websocket.service.RealtimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Achievement services.
 */
@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock
    private AchievementMapper achievementMapper;

    @Mock
    private UserAchievementMapper userAchievementMapper;

    @Mock
    private RealtimeService realtimeService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AchievementService achievementService;

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

    // ==================== AchievementServiceImpl Tests ====================

    @Nested
    @DisplayName("Achievement CRUD Tests")
    class AchievementCRUDTests {

        @Test
        @DisplayName("should create achievement successfully")
        void shouldCreateAchievementSuccessfully() {
            // Arrange
            AchievementDTO dto = createTestAchievementDTO();
            when(achievementMapper.findByKey(dto.getKey())).thenReturn(null);
            when(achievementMapper.insert(any(Achievement.class))).thenAnswer(invocation -> {
                Achievement a = invocation.getArgument(0);
                a.setId("new-id");
                return 1;
            });

            // Act
            AchievementVO result = achievementServiceImpl.create(dto);

            // Assert
            assertNotNull(result);
            assertEquals(dto.getKey(), result.getKey());
            assertEquals(dto.getName(), result.getName());
            verify(achievementMapper).insert(any(Achievement.class));
        }

        @Test
        @DisplayName("should throw exception when creating achievement with duplicate key")
        void shouldThrowExceptionWhenCreatingWithDuplicateKey() {
            // Arrange
            AchievementDTO dto = createTestAchievementDTO();
            Achievement existing = createTestAchievement();
            existing.setKey(dto.getKey());
            when(achievementMapper.findByKey(dto.getKey())).thenReturn(existing);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> achievementServiceImpl.create(dto));
            assertEquals(ErrorCode.CONFLICT.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("should return paginated achievements")
        void shouldReturnPaginatedAchievements() {
            // Arrange
            AchievementQueryDTO query = new AchievementQueryDTO();
            query.setPage(1);
            query.setLimit(10);

            Page<Achievement> mockPage = new Page<>(1, 10);
            Achievement achievement = createTestAchievement();
            mockPage.setRecords(List.of(achievement));
            mockPage.setTotal(1L);

            when(achievementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // Act
            var result = achievementServiceImpl.list(query);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getTotal());
            assertEquals(1, result.getItems().size());
        }

        @Test
        @DisplayName("should filter achievements by category")
        void shouldFilterAchievementsByCategory() {
            // Arrange
            AchievementQueryDTO query = new AchievementQueryDTO();
            query.setCategory("problem_solving");

            Page<Achievement> mockPage = new Page<>(1, 20);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0L);

            when(achievementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // Act
            achievementServiceImpl.list(query);

            // Assert
            verify(achievementMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("should return achievement by ID")
        void shouldReturnAchievementById() {
            // Arrange
            Achievement achievement = createTestAchievement();
            when(achievementMapper.selectById(ACHIEVEMENT_ID)).thenReturn(achievement);

            // Act
            AchievementVO result = achievementServiceImpl.getById(ACHIEVEMENT_ID);

            // Assert
            assertNotNull(result);
            assertEquals(ACHIEVEMENT_ID, result.getId());
            assertEquals("First Steps", result.getName());
        }

        @Test
        @DisplayName("should throw exception when achievement not found")
        void shouldThrowExceptionWhenAchievementNotFound() {
            // Arrange
            when(achievementMapper.selectById(ACHIEVEMENT_ID)).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> achievementServiceImpl.getById(ACHIEVEMENT_ID));
            assertEquals(ErrorCode.ACHIEVEMENT_NOT_FOUND.getCode(), exception.getCode());
        }

        @Test
        @DisplayName("should update achievement successfully")
        void shouldUpdateAchievementSuccessfully() {
            // Arrange
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

            // Act
            AchievementVO result = achievementServiceImpl.update(ACHIEVEMENT_ID, dto);

            // Assert
            assertNotNull(result);
            assertEquals("Updated Name", result.getName());
        }

        @Test
        @DisplayName("should delete achievement and associated user achievements")
        void shouldDeleteAchievementAndUserAchievements() {
            // Arrange
            Achievement achievement = createTestAchievement();
            when(achievementMapper.selectById(ACHIEVEMENT_ID)).thenReturn(achievement);
            when(userAchievementMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
            when(achievementMapper.deleteById(ACHIEVEMENT_ID)).thenReturn(1);

            // Act
            achievementServiceImpl.delete(ACHIEVEMENT_ID);

            // Assert
            verify(userAchievementMapper).delete(any(LambdaQueryWrapper.class));
            verify(achievementMapper).deleteById(ACHIEVEMENT_ID);
        }
    }

    @Nested
    @DisplayName("User Achievement Tests")
    class UserAchievementTests {

        @Test
        @DisplayName("should return user achievement progress")
        void shouldReturnUserAchievementProgress() {
            // Arrange
            Achievement achievement = createTestAchievement();
            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(Collections.emptyList());

            // Act
            List<AchievementProgressDTO> result = achievementServiceImpl.getUserAchievements(USER_ID);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertFalse(result.get(0).getEarned());
            assertEquals(1, result.get(0).getTarget());
        }

        @Test
        @DisplayName("should show earned achievement for user")
        void shouldShowEarnedAchievementForUser() {
            // Arrange
            Achievement achievement = createTestAchievement();
            UserAchievement userAchievement = new UserAchievement();
            userAchievement.setAchievementId(ACHIEVEMENT_ID);
            userAchievement.setUserId(USER_ID);
            userAchievement.setEarnedAt(LocalDateTime.now());

            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(List.of(userAchievement));

            // Act
            List<AchievementProgressDTO> result = achievementServiceImpl.getUserAchievements(USER_ID);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.get(0).getEarned());
            assertNotNull(result.get(0).getEarnedAt());
        }

        @Test
        @DisplayName("should return user points correctly")
        void shouldReturnUserPointsCorrectly() {
            // Arrange
            Achievement achievement = createTestAchievement();
            UserAchievement userAchievement = new UserAchievement();
            userAchievement.setAchievementId(ACHIEVEMENT_ID);
            userAchievement.setUserId(USER_ID);
            userAchievement.setEarnedAt(LocalDateTime.now());

            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(List.of(userAchievement));
            when(achievementMapper.selectById(ACHIEVEMENT_ID)).thenReturn(achievement);

            // Act
            UserPointsVO result = achievementServiceImpl.getUserPoints(USER_ID);

            // Assert
            assertNotNull(result);
            assertEquals(10, result.getTotalPoints());
            assertEquals(1, result.getAchievementsEarned());
        }

        @Test
        @DisplayName("should return zero points when no achievements earned")
        void shouldReturnZeroPointsWhenNoAchievementsEarned() {
            // Arrange
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(Collections.emptyList());

            // Act
            UserPointsVO result = achievementServiceImpl.getUserPoints(USER_ID);

            // Assert
            assertNotNull(result);
            assertEquals(0, result.getTotalPoints());
            assertEquals(0, result.getAchievementsEarned());
        }
    }

    // ==================== AchievementTriggerServiceImpl Tests ====================

    @Nested
    @DisplayName("Achievement Trigger Tests")
    class AchievementTriggerTests {

        @Test
        @DisplayName("should award achievement when target is met")
        void shouldAwardAchievementWhenTargetMet() {
            // Arrange
            Achievement achievement = createTestAchievement();
            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));
            when(userAchievementMapper.findByUserAndAchievement(USER_ID, ACHIEVEMENT_ID)).thenReturn(null);
            when(userAchievementMapper.insert(any(UserAchievement.class))).thenReturn(1);

            // Act — trigger methods are now void + async; call checkAndAwardAchievements directly in tests
            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(USER_ID, AchievementType.PROBLEMS_SOLVED, 1);

            // Assert
            assertEquals(1, awarded.size());
            assertEquals(ACHIEVEMENT_ID, awarded.get(0));
            verify(userAchievementMapper).insert(any(UserAchievement.class));
            verify(realtimeService).sendNotification(eq(USER_ID), any(BadgeEarnedPayload.class));
            verify(eventPublisher).publishEvent(any(AchievementEarnedEvent.class));
        }

        @Test
        @DisplayName("should not award achievement when target not met")
        void shouldNotAwardAchievementWhenTargetNotMet() {
            // Arrange
            Achievement achievement = createTestAchievement();
            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));

            // Act
            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(USER_ID, AchievementType.PROBLEMS_SOLVED, 0);

            // Assert
            assertTrue(awarded.isEmpty());
            verify(userAchievementMapper, never()).insert(any(UserAchievement.class));
        }

        @Test
        @DisplayName("should not award achievement when already earned")
        void shouldNotAwardAchievementWhenAlreadyEarned() {
            // Arrange
            Achievement achievement = createTestAchievement();
            UserAchievement existing = new UserAchievement();
            existing.setAchievementId(ACHIEVEMENT_ID);
            existing.setUserId(USER_ID);

            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));
            when(userAchievementMapper.findByUserAndAchievement(USER_ID, ACHIEVEMENT_ID)).thenReturn(existing);

            // Act
            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(USER_ID, AchievementType.PROBLEMS_SOLVED, 1);

            // Assert
            assertTrue(awarded.isEmpty());
            verify(userAchievementMapper, never()).insert(any(UserAchievement.class));
        }

        @Test
        @DisplayName("should check contest participation achievement")
        void shouldCheckContestParticipationAchievement() {
            // Arrange
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
            when(userAchievementMapper.findByUserAndAchievement(USER_ID, "contest-achievement")).thenReturn(null);
            when(userAchievementMapper.insert(any(UserAchievement.class))).thenReturn(1);

            // Act
            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(USER_ID, AchievementType.CONTEST_PARTICIPATION, 1);

            // Assert
            assertEquals(1, awarded.size());
        }

        @Test
        @DisplayName("should check solution written achievement")
        void shouldCheckSolutionWrittenAchievement() {
            // Arrange
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
            when(userAchievementMapper.findByUserAndAchievement(USER_ID, "solution-achievement")).thenReturn(null);
            when(userAchievementMapper.insert(any(UserAchievement.class))).thenReturn(1);

            // Act
            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(USER_ID, AchievementType.SOLUTIONS_WRITTEN, 1);

            // Assert
            assertEquals(1, awarded.size());
        }

        @Test
        @DisplayName("should check streak achievement")
        void shouldCheckStreakAchievement() {
            // Arrange
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
            when(userAchievementMapper.findByUserAndAchievement(USER_ID, "streak-achievement")).thenReturn(null);
            when(userAchievementMapper.insert(any(UserAchievement.class))).thenReturn(1);

            // Act
            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(USER_ID, AchievementType.STREAK_DAYS, 7);

            // Assert
            assertEquals(1, awarded.size());
        }

        @Test
        @DisplayName("should check generic achievement type")
        void shouldCheckGenericAchievementType() {
            // Arrange
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
            when(userAchievementMapper.findByUserAndAchievement(USER_ID, "rating-achievement")).thenReturn(null);
            when(userAchievementMapper.insert(any(UserAchievement.class))).thenReturn(1);

            // Act
            List<String> awarded = achievementTriggerService.checkAndAwardAchievements(
                    USER_ID, AchievementType.RATING_MILESTONE, 1500);

            // Assert
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
