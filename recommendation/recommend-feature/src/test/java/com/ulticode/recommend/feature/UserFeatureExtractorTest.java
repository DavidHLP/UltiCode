package com.ulticode.recommend.feature;

import com.ulticode.recommend.feature.model.UserFeatures;
import com.ulticode.recommend.feature.model.UserSubmission;
import com.ulticode.recommend.feature.model.ProblemInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserFeatureExtractor Tests")
class UserFeatureExtractorTest {

    private UserFeatureExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new UserFeatureExtractor();
    }

    @Nested
    @DisplayName("extractFeatures Method Tests")
    class ExtractFeaturesTests {

        @Test
        @DisplayName("Should extract all features from user submissions")
        void shouldExtractAllFeatures() {
            // Arrange
            String userId = "user123";
            LocalDateTime now = LocalDateTime.now();

            List<ProblemInfo> problems = Arrays.asList(
                createProblem(1L, "Easy", Set.of("array", "sorting")),
                createProblem(2L, "Easy", Set.of("array")),
                createProblem(3L, "Medium", Set.of("dynamic-programming")),
                createProblem(4L, "Medium", Set.of("dynamic-programming", "array")),
                createProblem(5L, "Hard", Set.of("graph"))
            );

            List<UserSubmission> submissions = Arrays.asList(
                // Easy problems - 3 accepted, 1 failed
                createSubmission(1L, true, now.minusDays(10)),
                createSubmission(2L, true, now.minusDays(8)),
                createSubmission(2L, false, now.minusDays(8)), // retry
                createSubmission(1L, true, now.minusDays(5)),
                // Medium problems - 1 accepted, 2 failed
                createSubmission(3L, false, now.minusDays(3)),
                createSubmission(4L, true, now.minusDays(2)),
                createSubmission(3L, false, now.minusDays(1)),
                // Hard problems - all failed
                createSubmission(5L, false, now.minusDays(1)),
                // Recent submissions (last 7 days)
                createSubmission(1L, true, now.minusDays(1)),
                createSubmission(3L, false, now.minusDays(2))
            );

            // Act
            UserFeatures features = extractor.extractFeatures(userId, submissions, problems);

            // Assert
            assertNotNull(features);
            assertEquals(userId, features.getUserId());

            // Activity features
            assertTrue(features.getActivityLevel() >= 0 && features.getActivityLevel() <= 1);
            assertEquals(10, features.getTotalSubmissions());
            // Within last 7 days: days 1, 2, 3, 5 (not 8, 10) = 7 submissions
            assertEquals(7, features.getRecentSubmissions());

            // Skill features
            assertTrue(features.getEasySuccessRate() >= 0);
            assertTrue(features.getMediumSuccessRate() >= 0);
            assertTrue(features.getHardSuccessRate() >= 0);
            assertNotNull(features.getSkillLevel());

            // Tag features
            assertNotNull(features.getTagPreferences());
            assertNotNull(features.getTagMastery());
            assertNotNull(features.getStrongTags());
            assertNotNull(features.getWeakTags());

            // Learning features
            assertTrue(features.getLearningVelocity() >= 0);
            assertTrue(features.getStreakDays() >= 0);
            assertTrue(features.getConsistency() >= 0 && features.getConsistency() <= 1);
        }

        @Test
        @DisplayName("Should handle empty submissions")
        void shouldHandleEmptySubmissions() {
            // Arrange
            String userId = "user123";
            List<UserSubmission> submissions = Collections.emptyList();
            List<ProblemInfo> problems = Arrays.asList(
                createProblem(1L, "Easy", Set.of("array"))
            );

            // Act
            UserFeatures features = extractor.extractFeatures(userId, submissions, problems);

            // Assert
            assertNotNull(features);
            assertEquals(userId, features.getUserId());
            assertEquals(0, features.getTotalSubmissions());
            assertEquals(0, features.getRecentSubmissions());
            assertEquals(0.0, features.getActivityLevel());
            assertEquals(0.0, features.getEasySuccessRate());
            assertEquals(0.0, features.getMediumSuccessRate());
            assertEquals(0.0, features.getHardSuccessRate());
            assertTrue(features.getTagPreferences().isEmpty());
            assertTrue(features.getTagMastery().isEmpty());
            assertTrue(features.getStrongTags().isEmpty());
            assertTrue(features.getWeakTags().isEmpty());
        }

        @Test
        @DisplayName("Should handle null inputs gracefully")
        void shouldHandleNullInputs() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                extractor.extractFeatures(null, Collections.emptyList(), Collections.emptyList()));

            assertThrows(IllegalArgumentException.class, () ->
                extractor.extractFeatures("user1", null, Collections.emptyList()));
        }
    }

    @Nested
    @DisplayName("calculateActivityLevel Tests")
    class CalculateActivityLevelTests {

        @Test
        @DisplayName("Should return 0 for no submissions")
        void shouldReturnZeroForNoSubmissions() {
            double level = extractor.calculateActivityLevel(Collections.emptyList());
            assertEquals(0.0, level);
        }

        @Test
        @DisplayName("Should increase with more recent submissions")
        void shouldIncreaseWithMoreRecentSubmissions() {
            LocalDateTime now = LocalDateTime.now();

            List<UserSubmission> fewSubmissions = Arrays.asList(
                createSubmission(1L, true, now.minusDays(1)),
                createSubmission(2L, true, now.minusDays(2))
            );

            List<UserSubmission> manySubmissions = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                manySubmissions.add(createSubmission((long) i, true, now.minusDays(i % 7)));
            }

            double fewLevel = extractor.calculateActivityLevel(fewSubmissions);
            double manyLevel = extractor.calculateActivityLevel(manySubmissions);

            assertTrue(fewLevel >= 0 && fewLevel <= 1);
            assertTrue(manyLevel >= 0 && manyLevel <= 1);
            assertTrue(manyLevel > fewLevel, "More submissions should result in higher activity level");
        }

        @Test
        @DisplayName("Should cap at 1.0 for very active users")
        void shouldCapAtOneForVeryActiveUsers() {
            LocalDateTime now = LocalDateTime.now();
            List<UserSubmission> manySubmissions = new ArrayList<>();

            // Create 100 submissions in last 7 days
            for (int i = 0; i < 100; i++) {
                manySubmissions.add(createSubmission((long) i, true, now.minusHours(i)));
            }

            double level = extractor.calculateActivityLevel(manySubmissions);
            assertTrue(level >= 0 && level <= 1.0);
        }
    }

    @Nested
    @DisplayName("calculateSuccessRates Tests")
    class CalculateSuccessRatesTests {

        @Test
        @DisplayName("Should calculate correct success rates by difficulty")
        void shouldCalculateCorrectSuccessRatesByDifficulty() {
            LocalDateTime now = LocalDateTime.now();

            List<ProblemInfo> problems = Arrays.asList(
                createProblem(1L, "Easy", Set.of("array")),
                createProblem(2L, "Easy", Set.of("array")),
                createProblem(3L, "Medium", Set.of("dp")),
                createProblem(4L, "Hard", Set.of("graph"))
            );

            List<UserSubmission> submissions = Arrays.asList(
                // Easy: 3 accepted out of 4 = 75%
                createSubmission(1L, true, now),
                createSubmission(1L, false, now),
                createSubmission(2L, true, now),
                createSubmission(2L, true, now),
                // Medium: 0 accepted out of 2 = 0%
                createSubmission(3L, false, now),
                createSubmission(3L, false, now),
                // Hard: 1 accepted out of 1 = 100%
                createSubmission(4L, true, now)
            );

            Map<String, Double> rates = extractor.calculateSuccessRates(submissions, problems);

            assertEquals(0.75, rates.get("Easy"), 0.01);
            assertEquals(0.0, rates.get("Medium"), 0.01);
            assertEquals(1.0, rates.get("Hard"), 0.01);
        }

        @Test
        @DisplayName("Should return 0 for difficulties with no submissions")
        void shouldReturnZeroForDifficultiesWithNoSubmissions() {
            List<ProblemInfo> problems = Arrays.asList(
                createProblem(1L, "Easy", Set.of("array")),
                createProblem(2L, "Medium", Set.of("dp")),
                createProblem(3L, "Hard", Set.of("graph"))
            );

            List<UserSubmission> submissions = Arrays.asList(
                createSubmission(1L, true, LocalDateTime.now())
            );

            Map<String, Double> rates = extractor.calculateSuccessRates(submissions, problems);

            assertEquals(1.0, rates.get("Easy"), 0.01);
            assertEquals(0.0, rates.get("Medium"), 0.01);
            assertEquals(0.0, rates.get("Hard"), 0.01);
        }

        @Test
        @DisplayName("Should handle empty submissions")
        void shouldHandleEmptySubmissionsForSuccessRates() {
            List<ProblemInfo> problems = Arrays.asList(
                createProblem(1L, "Easy", Set.of("array"))
            );

            Map<String, Double> rates = extractor.calculateSuccessRates(Collections.emptyList(), problems);

            assertEquals(0.0, rates.get("Easy"), 0.01);
            assertEquals(0.0, rates.get("Medium"), 0.01);
            assertEquals(0.0, rates.get("Hard"), 0.01);
        }
    }

    @Nested
    @DisplayName("calculateTagPreferences Tests")
    class CalculateTagPreferencesTests {

        @Test
        @DisplayName("Should calculate tag preference based on problem count")
        void shouldCalculateTagPreferenceBasedOnProblemCount() {
            LocalDateTime now = LocalDateTime.now();

            List<ProblemInfo> problems = Arrays.asList(
                createProblem(1L, "Easy", Set.of("array", "sorting")),
                createProblem(2L, "Easy", Set.of("array")),
                createProblem(3L, "Medium", Set.of("dynamic-programming")),
                createProblem(4L, "Medium", Set.of("array", "dynamic-programming"))
            );

            List<UserSubmission> submissions = Arrays.asList(
                createSubmission(1L, true, now),
                createSubmission(2L, true, now),
                createSubmission(3L, true, now),
                createSubmission(4L, true, now)
            );

            Map<String, Double> preferences = extractor.calculateTagPreferences(submissions, problems);

            // array appears in 3 out of 4 problems = 0.75
            assertEquals(0.75, preferences.get("array"), 0.01);
            // dynamic-programming appears in 2 out of 4 = 0.5
            assertEquals(0.5, preferences.get("dynamic-programming"), 0.01);
            // sorting appears in 1 out of 4 = 0.25
            assertEquals(0.25, preferences.get("sorting"), 0.01);
        }

        @Test
        @DisplayName("Should handle problems with no tags")
        void shouldHandleProblemsWithNoTags() {
            LocalDateTime now = LocalDateTime.now();

            List<ProblemInfo> problems = Arrays.asList(
                createProblem(1L, "Easy", Collections.emptySet()),
                createProblem(2L, "Easy", null)
            );

            List<UserSubmission> submissions = Arrays.asList(
                createSubmission(1L, true, now),
                createSubmission(2L, true, now)
            );

            Map<String, Double> preferences = extractor.calculateTagPreferences(submissions, problems);

            assertTrue(preferences.isEmpty());
        }
    }

    @Nested
    @DisplayName("calculateTagMastery Tests")
    class CalculateTagMasteryTests {

        @Test
        @DisplayName("Should calculate mastery based on accepted/attempted ratio")
        void shouldCalculateMasteryBasedOnAcceptedAttemptedRatio() {
            LocalDateTime now = LocalDateTime.now();

            List<ProblemInfo> problems = Arrays.asList(
                createProblem(1L, "Easy", Set.of("array")),
                createProblem(2L, "Easy", Set.of("array")),
                createProblem(3L, "Easy", Set.of("sorting"))
            );

            List<UserSubmission> submissions = Arrays.asList(
                // array: 2 accepted, 1 failed = 0.667 mastery
                createSubmission(1L, true, now),
                createSubmission(2L, true, now),
                createSubmission(1L, false, now),
                // sorting: 0 accepted, 2 failed = 0.0 mastery
                createSubmission(3L, false, now),
                createSubmission(3L, false, now)
            );

            Map<String, Double> mastery = extractor.calculateTagMastery(submissions, problems);

            assertEquals(2.0/3.0, mastery.get("array"), 0.01);
            assertEquals(0.0, mastery.get("sorting"), 0.01);
        }

        @Test
        @DisplayName("Should handle tags with no attempts")
        void shouldHandleTagsWithNoAttempts() {
            List<ProblemInfo> problems = Arrays.asList(
                createProblem(1L, "Easy", Set.of("array")),
                createProblem(2L, "Easy", Set.of("sorting"))
            );

            List<UserSubmission> submissions = Arrays.asList(
                createSubmission(1L, true, LocalDateTime.now())
            );

            Map<String, Double> mastery = extractor.calculateTagMastery(submissions, problems);

            assertEquals(1.0, mastery.get("array"), 0.01);
            // sorting should not appear as it has no attempts
            assertFalse(mastery.containsKey("sorting"));
        }
    }

    @Nested
    @DisplayName("identifyStrongWeakTags Tests")
    class IdentifyStrongWeakTagsTests {

        @Test
        @DisplayName("Should identify strong tags with mastery > 0.7")
        void shouldIdentifyStrongTagsWithMasteryAboveThreshold() {
            Map<String, Double> tagMastery = new HashMap<>();
            tagMastery.put("array", 0.9);
            tagMastery.put("sorting", 0.75);
            tagMastery.put("dynamic-programming", 0.5);
            tagMastery.put("graph", 0.2);  // Changed to 0.2 to be clearly weak (< 0.3)

            Map<String, Set<String>> result = extractor.identifyStrongWeakTags(tagMastery);

            Set<String> strongTags = result.get("strong");
            Set<String> weakTags = result.get("weak");

            assertTrue(strongTags.contains("array"));
            assertTrue(strongTags.contains("sorting"));
            assertFalse(strongTags.contains("dynamic-programming"));
            assertFalse(strongTags.contains("graph"));

            assertFalse(weakTags.contains("array"));
            assertFalse(weakTags.contains("sorting"));
            assertTrue(weakTags.contains("graph"));
            assertFalse(weakTags.contains("dynamic-programming")); // 0.5 is neither strong nor weak
        }

        @Test
        @DisplayName("Should handle empty tag mastery")
        void shouldHandleEmptyTagMastery() {
            Map<String, Set<String>> result = extractor.identifyStrongWeakTags(new HashMap<>());

            assertTrue(result.get("strong").isEmpty());
            assertTrue(result.get("weak").isEmpty());
        }
    }

    @Nested
    @DisplayName("determineSkillLevel Tests")
    class DetermineSkillLevelTests {

        @Test
        @DisplayName("Should return beginner for low success rates")
        void shouldReturnBeginnerForLowSuccessRates() {
            Map<String, Double> successRates = new HashMap<>();
            successRates.put("Easy", 0.3);
            successRates.put("Medium", 0.1);
            successRates.put("Hard", 0.0);

            String level = extractor.determineSkillLevel(successRates);
            assertEquals("beginner", level);
        }

        @Test
        @DisplayName("Should return intermediate for medium success rates")
        void shouldReturnIntermediateForMediumSuccessRates() {
            Map<String, Double> successRates = new HashMap<>();
            successRates.put("Easy", 0.7);
            successRates.put("Medium", 0.5);
            successRates.put("Hard", 0.2);

            String level = extractor.determineSkillLevel(successRates);
            assertEquals("intermediate", level);
        }

        @Test
        @DisplayName("Should return advanced for high success rates")
        void shouldReturnAdvancedForHighSuccessRates() {
            Map<String, Double> successRates = new HashMap<>();
            successRates.put("Easy", 0.9);
            successRates.put("Medium", 0.8);
            successRates.put("Hard", 0.5);

            String level = extractor.determineSkillLevel(successRates);
            assertEquals("advanced", level);
        }

        @Test
        @DisplayName("Should handle empty success rates")
        void shouldHandleEmptySuccessRates() {
            String level = extractor.determineSkillLevel(new HashMap<>());
            assertEquals("beginner", level);
        }
    }

    @Nested
    @DisplayName("Learning Features Tests")
    class LearningFeaturesTests {

        @Test
        @DisplayName("Should calculate streak days correctly")
        void shouldCalculateStreakDaysCorrectly() {
            LocalDateTime now = LocalDateTime.now();

            List<UserSubmission> submissions = Arrays.asList(
                createSubmission(1L, true, now),
                createSubmission(2L, true, now.minusDays(1)),
                createSubmission(3L, true, now.minusDays(2)),
                createSubmission(4L, true, now.minusDays(3)),
                // Gap here - day 4 and 5 missing
                createSubmission(5L, true, now.minusDays(6))
            );

            List<ProblemInfo> problems = Arrays.asList(
                createProblem(1L, "Easy", Set.of("array")),
                createProblem(2L, "Easy", Set.of("array")),
                createProblem(3L, "Easy", Set.of("array")),
                createProblem(4L, "Easy", Set.of("array")),
                createProblem(5L, "Easy", Set.of("array"))
            );

            UserFeatures features = extractor.extractFeatures("user1", submissions, problems);

            // Streak should be 4 (days 0, 1, 2, 3)
            assertEquals(4, features.getStreakDays());
        }

        @Test
        @DisplayName("Should calculate consistency score")
        void shouldCalculateConsistencyScore() {
            LocalDateTime now = LocalDateTime.now();

            // Regular submissions over 7 days
            List<UserSubmission> consistentSubmissions = new ArrayList<>();
            for (int day = 0; day < 7; day++) {
                consistentSubmissions.add(createSubmission((long) day, true, now.minusDays(day)));
            }

            List<ProblemInfo> problems = new ArrayList<>();
            for (long i = 0; i < 7; i++) {
                problems.add(createProblem(i, "Easy", Set.of("array")));
            }

            UserFeatures features = extractor.extractFeatures("user1", consistentSubmissions, problems);

            assertTrue(features.getConsistency() > 0.5, "Regular submissions should have high consistency");
        }
    }

    // Helper methods

    private UserSubmission createSubmission(Long problemId, boolean accepted, LocalDateTime timestamp) {
        return UserSubmission.builder()
            .problemId(problemId)
            .accepted(accepted)
            .timestamp(timestamp)
            .build();
    }

    private ProblemInfo createProblem(Long id, String difficulty, Set<String> tags) {
        return ProblemInfo.builder()
            .problemId(id)
            .difficulty(difficulty)
            .tags(tags != null ? tags : Collections.emptySet())
            .build();
    }
}
