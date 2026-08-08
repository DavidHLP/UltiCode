package com.ulticode.modules.achievement.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.AchievementErrorCode;
import com.ulticode.modules.achievement.dto.AchievementProgressDTO;
import com.ulticode.modules.achievement.dto.AchievementQueryDTO;
import com.ulticode.modules.achievement.dto.AchievementVO;
import com.ulticode.modules.achievement.dto.UserPointsVO;
import com.ulticode.modules.achievement.entity.Achievement;
import com.ulticode.modules.achievement.entity.UserAchievement;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import com.ulticode.modules.achievement.mapper.UserAchievementMapper;
import com.ulticode.app.api.service.SubmissionUserStatsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultAchievementProjection}.
 *
 * <p>Migrated from {@code AchievementServiceTest} when the read paths were
 * extracted from {@code AchievementServiceImpl} (ADR-0005). Behaviour is
 * unchanged &mdash; the assertions are identical, only the SUT moved.</p>
 */
@ExtendWith(MockitoExtension.class)
class AchievementProjectionTest {

    @Mock
    private AchievementMapper achievementMapper;

    @Mock
    private UserAchievementMapper userAchievementMapper;

    @Mock
    private SubmissionUserStatsPort submissionUserStats;

    @InjectMocks
    private DefaultAchievementProjection projection;

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

    private Achievement createAchievementWithCriteria(String key, String type, int target) {
        Achievement a = new Achievement();
        a.setId("ach-" + key);
        a.setKey(key);
        a.setName("Name " + key);
        a.setDescription("Desc " + key);
        a.setCategory("problems");
        a.setTier(1);
        a.setPoints(10);
        a.setIsActive(true);
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("type", type);
        criteria.put("target", target);
        a.setCriteria(criteria);
        return a;
    }

    @Nested
    @DisplayName("AchievementProjection#getUserAchievements progress tests")
    class GetUserAchievementsProgressTests {

        @Test
        @DisplayName("problems_solved criteria → progress = countAcceptedProblemsByUserId")
        void progress_fromProblemsSolved() {
            Achievement a = createAchievementWithCriteria("ps", "problems_solved", 10);
            when(achievementMapper.findAllActive()).thenReturn(List.of(a));
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(List.of());
            when(submissionUserStats.countAcceptedProblemsByUserId(USER_ID)).thenReturn(7L);
            when(submissionUserStats.countByUserId(USER_ID)).thenReturn(20L);

            List<AchievementProgressDTO> result = projection.getUserAchievements(USER_ID);

            assertEquals(1, result.size());
            assertEquals(7, result.get(0).getProgress(),
                    "progress must equal countAcceptedProblemsByUserId for problems_solved criteria");
            assertEquals(10, result.get(0).getTarget());
            assertFalse(result.get(0).getEarned());
            verify(submissionUserStats).countAcceptedProblemsByUserId(USER_ID);
        }

        @Test
        @DisplayName("submissions_made criteria → progress = countByUserId")
        void progress_fromSubmissionsMade() {
            Achievement a = createAchievementWithCriteria("sm", "submissions_made", 100);
            when(achievementMapper.findAllActive()).thenReturn(List.of(a));
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(List.of());
            when(submissionUserStats.countAcceptedProblemsByUserId(USER_ID)).thenReturn(3L);
            when(submissionUserStats.countByUserId(USER_ID)).thenReturn(42L);

            List<AchievementProgressDTO> result = projection.getUserAchievements(USER_ID);

            assertEquals(1, result.size());
            assertEquals(42, result.get(0).getProgress(),
                    "progress must equal countByUserId for submissions_made criteria");
            assertEquals(100, result.get(0).getTarget());
        }

        @Test
        @DisplayName("null criteria → progress=0, target=0 (does not throw)")
        void progress_nullCriteria_zero() {
            Achievement a = new Achievement();
            a.setId("ach-null");
            a.setKey("no_criteria");
            a.setName("No criteria");
            a.setIsActive(true);
            a.setCriteria(null);
            when(achievementMapper.findAllActive()).thenReturn(List.of(a));
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(List.of());
            when(submissionUserStats.countAcceptedProblemsByUserId(USER_ID)).thenReturn(0L);
            when(submissionUserStats.countByUserId(USER_ID)).thenReturn(0L);

            List<AchievementProgressDTO> result = projection.getUserAchievements(USER_ID);

            assertEquals(1, result.size());
            assertEquals(0, result.get(0).getProgress());
            assertEquals(0, result.get(0).getTarget());
        }
    }

    @Nested
    @DisplayName("list & getById tests")
    class ListAndGetByIdTests {

        @Test
        @DisplayName("should return paginated achievements")
        void shouldReturnPaginatedAchievements() {
            AchievementQueryDTO query = new AchievementQueryDTO();
            query.setPage(1);
            query.setLimit(10);

            Page<Achievement> mockPage = new Page<>(1, 10);
            Achievement achievement = createTestAchievement();
            mockPage.setRecords(List.of(achievement));
            mockPage.setTotal(1L);

            when(achievementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            var result = projection.list(query);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
            assertEquals(1, result.getItems().size());
        }

        @Test
        @DisplayName("should filter achievements by category")
        void shouldFilterAchievementsByCategory() {
            AchievementQueryDTO query = new AchievementQueryDTO();
            query.setCategory("problems");

            Page<Achievement> mockPage = new Page<>(1, 20);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0L);

            when(achievementMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            projection.list(query);

            verify(achievementMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("should return achievement by ID")
        void shouldReturnAchievementById() {
            Achievement achievement = createTestAchievement();
            when(achievementMapper.selectById(ACHIEVEMENT_ID)).thenReturn(achievement);

            AchievementVO result = projection.getById(ACHIEVEMENT_ID);

            assertNotNull(result);
            assertEquals(ACHIEVEMENT_ID, result.getId());
            assertEquals("First Steps", result.getName());
        }

        @Test
        @DisplayName("should throw exception when achievement not found")
        void shouldThrowExceptionWhenAchievementNotFound() {
            when(achievementMapper.selectById(ACHIEVEMENT_ID)).thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> projection.getById(ACHIEVEMENT_ID));
            assertEquals(AchievementErrorCode.ACHIEVEMENT_NOT_FOUND.code(), exception.getCode());
        }
    }

    @Nested
    @DisplayName("User achievement & points tests")
    class UserAchievementTests {

        @Test
        @DisplayName("should return user achievement progress")
        void shouldReturnUserAchievementProgress() {
            Achievement achievement = createTestAchievement();
            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(Collections.emptyList());

            List<AchievementProgressDTO> result = projection.getUserAchievements(USER_ID);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertFalse(result.get(0).getEarned());
            assertEquals(1, result.get(0).getTarget());
        }

        @Test
        @DisplayName("should show earned achievement for user")
        void shouldShowEarnedAchievementForUser() {
            Achievement achievement = createTestAchievement();
            UserAchievement userAchievement = new UserAchievement();
            userAchievement.setAchievementId(ACHIEVEMENT_ID);
            userAchievement.setUserId(USER_ID);
            userAchievement.setEarnedAt(LocalDateTime.now());

            when(achievementMapper.findAllActive()).thenReturn(List.of(achievement));
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(List.of(userAchievement));

            List<AchievementProgressDTO> result = projection.getUserAchievements(USER_ID);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.get(0).getEarned());
            assertNotNull(result.get(0).getEarnedAt());
        }

        @Test
        @DisplayName("should return user points correctly")
        void shouldReturnUserPointsCorrectly() {
            Achievement achievement = createTestAchievement();
            UserAchievement userAchievement = new UserAchievement();
            userAchievement.setAchievementId(ACHIEVEMENT_ID);
            userAchievement.setUserId(USER_ID);
            userAchievement.setEarnedAt(LocalDateTime.now());

            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(List.of(userAchievement));
            when(achievementMapper.selectBatchIds(List.of(ACHIEVEMENT_ID))).thenReturn(List.of(achievement));

            UserPointsVO result = projection.getUserPoints(USER_ID);

            assertNotNull(result);
            assertEquals(10, result.getTotalPoints());
            assertEquals(1, result.getAchievementsEarned());
        }

        @Test
        @DisplayName("should return zero points when no achievements earned")
        void shouldReturnZeroPointsWhenNoAchievementsEarned() {
            when(userAchievementMapper.findByUserId(USER_ID)).thenReturn(Collections.emptyList());

            UserPointsVO result = projection.getUserPoints(USER_ID);

            assertNotNull(result);
            assertEquals(0, result.getTotalPoints());
            assertEquals(0, result.getAchievementsEarned());
        }
    }
}
