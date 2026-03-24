package com.ulticode.recommend.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserProfile Tests")
class UserProfileTest {

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create profile using builder pattern")
        void shouldCreateProfileUsingBuilder() {
            Map<String, Double> tagMastery = new HashMap<>();
            tagMastery.put("array", 0.8);
            tagMastery.put("dynamic-programming", 0.3);

            Map<String, Integer> difficultyStats = new HashMap<>();
            difficultyStats.put("Easy", 50);
            difficultyStats.put("Medium", 30);

            UserProfile profile = UserProfile.builder()
                    .userId("user123")
                    .rating(1500)
                    .maxRating(1650)
                    .preferredLanguage("java")
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .tagMastery(tagMastery)
                    .difficultyStats(difficultyStats)
                    .preferredDifficulty("Medium")
                    .totalSolved(80)
                    .totalAttempts(100)
                    .build();

            assertNotNull(profile);
            assertEquals("user123", profile.getUserId());
            assertEquals(1500, profile.getRating());
            assertEquals(1650, profile.getMaxRating());
            assertEquals("java", profile.getPreferredLanguage());
            assertEquals(Set.of(1L, 2L, 3L), profile.getSolvedProblems());
            assertEquals(tagMastery, profile.getTagMastery());
            assertEquals(difficultyStats, profile.getDifficultyStats());
            assertEquals("Medium", profile.getPreferredDifficulty());
            assertEquals(80, profile.getTotalSolved());
            assertEquals(100, profile.getTotalAttempts());
        }
    }

    @Nested
    @DisplayName("Get Weak Tags Tests")
    class GetWeakTagsTests {

        @Test
        @DisplayName("Should return weak tags below threshold")
        void shouldReturnWeakTagsBelowThreshold() {
            Map<String, Double> tagMastery = new HashMap<>();
            tagMastery.put("array", 0.8);
            tagMastery.put("dynamic-programming", 0.3);
            tagMastery.put("tree", 0.5);
            tagMastery.put("graph", 0.2);

            UserProfile profile = UserProfile.builder()
                    .tagMastery(tagMastery)
                    .build();

            Set<String> weakTags = profile.getWeakTags(0.5);

            assertTrue(weakTags.contains("dynamic-programming"));
            assertTrue(weakTags.contains("graph"));
            assertFalse(weakTags.contains("array"));
            assertFalse(weakTags.contains("tree"));
        }

        @Test
        @DisplayName("Should return empty set when all tags above threshold")
        void shouldReturnEmptySetWhenAllTagsAboveThreshold() {
            Map<String, Double> tagMastery = new HashMap<>();
            tagMastery.put("array", 0.8);
            tagMastery.put("tree", 0.9);

            UserProfile profile = UserProfile.builder()
                    .tagMastery(tagMastery)
                    .build();

            Set<String> weakTags = profile.getWeakTags(0.5);

            assertTrue(weakTags.isEmpty());
        }

        @Test
        @DisplayName("Should return all tags when threshold is 1.0")
        void shouldReturnAllTagsWhenThresholdIsOne() {
            Map<String, Double> tagMastery = new HashMap<>();
            tagMastery.put("array", 0.8);
            tagMastery.put("tree", 0.9);

            UserProfile profile = UserProfile.builder()
                    .tagMastery(tagMastery)
                    .build();

            Set<String> weakTags = profile.getWeakTags(1.0);

            assertEquals(2, weakTags.size());
        }

        @Test
        @DisplayName("Should handle null tagMastery")
        void shouldHandleNullTagMastery() {
            UserProfile profile = UserProfile.builder()
                    .tagMastery(null)
                    .build();

            Set<String> weakTags = profile.getWeakTags(0.5);

            assertNotNull(weakTags);
            assertTrue(weakTags.isEmpty());
        }

        @Test
        @DisplayName("Should handle empty tagMastery")
        void shouldHandleEmptyTagMastery() {
            UserProfile profile = UserProfile.builder()
                    .tagMastery(new HashMap<>())
                    .build();

            Set<String> weakTags = profile.getWeakTags(0.5);

            assertNotNull(weakTags);
            assertTrue(weakTags.isEmpty());
        }
    }

    @Nested
    @DisplayName("Get Appropriate Difficulty Tests")
    class GetAppropriateDifficultyTests {

        @Test
        @DisplayName("Should return Easy for rating below 1200")
        void shouldReturnEasyForRatingBelow1200() {
            UserProfile profile = UserProfile.builder()
                    .rating(800)
                    .build();

            assertEquals("Easy", profile.getAppropriateDifficulty());
        }

        @Test
        @DisplayName("Should return Easy for rating at 1199")
        void shouldReturnEasyForRatingAt1199() {
            UserProfile profile = UserProfile.builder()
                    .rating(1199)
                    .build();

            assertEquals("Easy", profile.getAppropriateDifficulty());
        }

        @Test
        @DisplayName("Should return Medium for rating between 1200 and 1799")
        void shouldReturnMediumForRatingBetween1200And1799() {
            UserProfile profile1 = UserProfile.builder()
                    .rating(1200)
                    .build();

            UserProfile profile2 = UserProfile.builder()
                    .rating(1500)
                    .build();

            UserProfile profile3 = UserProfile.builder()
                    .rating(1799)
                    .build();

            assertEquals("Medium", profile1.getAppropriateDifficulty());
            assertEquals("Medium", profile2.getAppropriateDifficulty());
            assertEquals("Medium", profile3.getAppropriateDifficulty());
        }

        @Test
        @DisplayName("Should return Hard for rating 1800 and above")
        void shouldReturnHardForRating1800AndAbove() {
            UserProfile profile1 = UserProfile.builder()
                    .rating(1800)
                    .build();

            UserProfile profile2 = UserProfile.builder()
                    .rating(2200)
                    .build();

            assertEquals("Hard", profile1.getAppropriateDifficulty());
            assertEquals("Hard", profile2.getAppropriateDifficulty());
        }
    }

    @Nested
    @DisplayName("Has Solved Tests")
    class HasSolvedTests {

        @Test
        @DisplayName("Should return true for solved problem")
        void shouldReturnTrueForSolvedProblem() {
            UserProfile profile = UserProfile.builder()
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            assertTrue(profile.hasSolved(1L));
            assertTrue(profile.hasSolved(2L));
            assertTrue(profile.hasSolved(3L));
        }

        @Test
        @DisplayName("Should return false for unsolved problem")
        void shouldReturnFalseForUnsolvedProblem() {
            UserProfile profile = UserProfile.builder()
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            assertFalse(profile.hasSolved(4L));
            assertFalse(profile.hasSolved(999L));
        }

        @Test
        @DisplayName("Should handle null solvedProblems")
        void shouldHandleNullSolvedProblems() {
            UserProfile profile = UserProfile.builder()
                    .solvedProblems(null)
                    .build();

            assertFalse(profile.hasSolved(1L));
        }

        @Test
        @DisplayName("Should handle empty solvedProblems")
        void shouldHandleEmptySolvedProblems() {
            UserProfile profile = UserProfile.builder()
                    .solvedProblems(Set.of())
                    .build();

            assertFalse(profile.hasSolved(1L));
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should create profile with no-args constructor")
        void shouldCreateProfileWithNoArgsConstructor() {
            UserProfile profile = new UserProfile();
            assertNotNull(profile);
        }

        @Test
        @DisplayName("Should create profile with all-args constructor")
        void shouldCreateProfileWithAllArgsConstructor() {
            Map<String, Double> tagMastery = new HashMap<>();
            tagMastery.put("array", 0.8);

            Map<String, Integer> difficultyStats = new HashMap<>();
            difficultyStats.put("Easy", 50);

            UserProfile profile = new UserProfile(
                    "user123", 1500, 1650, "java",
                    Set.of(1L, 2L, 3L), tagMastery, difficultyStats,
                    "Medium", 80, 100
            );

            assertEquals("user123", profile.getUserId());
            assertEquals(1500, profile.getRating());
            assertEquals(1650, profile.getMaxRating());
            assertEquals("java", profile.getPreferredLanguage());
            assertEquals(Set.of(1L, 2L, 3L), profile.getSolvedProblems());
            assertEquals(80, profile.getTotalSolved());
            assertEquals(100, profile.getTotalAttempts());
        }
    }
}
