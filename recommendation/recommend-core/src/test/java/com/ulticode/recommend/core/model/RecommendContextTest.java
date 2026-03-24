package com.ulticode.recommend.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecommendContext Tests")
class RecommendContextTest {

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create context using builder pattern")
        void shouldCreateContextUsingBuilder() {
            RecommendContext context = RecommendContext.builder()
                    .userId("user123")
                    .size(20)
                    .scenario(RecommendContext.Scenario.WEAK_POINT)
                    .sourceProblemId(42L)
                    .targetTags(new String[]{"array", "dp"})
                    .minDifficulty("Easy")
                    .maxDifficulty("Hard")
                    .includeSolved(true)
                    .build();

            assertNotNull(context);
            assertEquals("user123", context.getUserId());
            assertEquals(20, context.getSize());
            assertEquals(RecommendContext.Scenario.WEAK_POINT, context.getScenario());
            assertEquals(42L, context.getSourceProblemId());
            assertArrayEquals(new String[]{"array", "dp"}, context.getTargetTags());
            assertEquals("Easy", context.getMinDifficulty());
            assertEquals("Hard", context.getMaxDifficulty());
            assertTrue(context.isIncludeSolved());
        }
    }

    @Nested
    @DisplayName("Default Values Tests")
    class DefaultValuesTests {

        @Test
        @DisplayName("Should have default size of 10")
        void shouldHaveDefaultSizeOf10() {
            RecommendContext context = RecommendContext.builder()
                    .userId("user123")
                    .build();

            assertEquals(10, context.getSize());
        }

        @Test
        @DisplayName("Should have default scenario of DAILY")
        void shouldHaveDefaultScenarioOfDaily() {
            RecommendContext context = RecommendContext.builder()
                    .userId("user123")
                    .build();

            assertEquals(RecommendContext.Scenario.DAILY, context.getScenario());
        }

        @Test
        @DisplayName("Should have default includeSolved of false")
        void shouldHaveDefaultIncludeSolvedOfFalse() {
            RecommendContext context = RecommendContext.builder()
                    .userId("user123")
                    .build();

            assertFalse(context.isIncludeSolved());
        }
    }

    @Nested
    @DisplayName("Scenario Enum Tests")
    class ScenarioEnumTests {

        @Test
        @DisplayName("Should have DAILY scenario")
        void shouldHaveDailyScenario() {
            assertEquals("DAILY", RecommendContext.Scenario.DAILY.name());
        }

        @Test
        @DisplayName("Should have SIMILAR scenario")
        void shouldHaveSimilarScenario() {
            assertEquals("SIMILAR", RecommendContext.Scenario.SIMILAR.name());
        }

        @Test
        @DisplayName("Should have WEAK_POINT scenario")
        void shouldHaveWeakPointScenario() {
            assertEquals("WEAK_POINT", RecommendContext.Scenario.WEAK_POINT.name());
        }

        @Test
        @DisplayName("Should have CHALLENGE scenario")
        void shouldHaveChallengeScenario() {
            assertEquals("CHALLENGE", RecommendContext.Scenario.CHALLENGE.name());
        }

        @Test
        @DisplayName("Should have exactly 4 scenarios")
        void shouldHaveExactly4Scenarios() {
            assertEquals(4, RecommendContext.Scenario.values().length);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle null optional fields")
        void shouldHandleNullOptionalFields() {
            RecommendContext context = RecommendContext.builder()
                    .userId("user123")
                    .sourceProblemId(null)
                    .targetTags(null)
                    .minDifficulty(null)
                    .maxDifficulty(null)
                    .build();

            assertEquals("user123", context.getUserId());
            assertNull(context.getSourceProblemId());
            assertNull(context.getTargetTags());
            assertNull(context.getMinDifficulty());
            assertNull(context.getMaxDifficulty());
        }

        @Test
        @DisplayName("Should create context with no-args constructor")
        void shouldCreateContextWithNoArgsConstructor() {
            RecommendContext context = new RecommendContext();
            assertNotNull(context);
        }

        @Test
        @DisplayName("Should create context with all-args constructor")
        void shouldCreateContextWithAllArgsConstructor() {
            RecommendContext context = new RecommendContext(
                    "user123", 20, RecommendContext.Scenario.SIMILAR,
                    42L, new String[]{"array"}, "Easy", "Medium", true
            );

            assertEquals("user123", context.getUserId());
            assertEquals(20, context.getSize());
            assertEquals(RecommendContext.Scenario.SIMILAR, context.getScenario());
            assertEquals(42L, context.getSourceProblemId());
            assertArrayEquals(new String[]{"array"}, context.getTargetTags());
            assertEquals("Easy", context.getMinDifficulty());
            assertEquals("Medium", context.getMaxDifficulty());
            assertTrue(context.isIncludeSolved());
        }

        @Test
        @DisplayName("Should allow custom size values")
        void shouldAllowCustomSizeValues() {
            RecommendContext context1 = RecommendContext.builder().size(1).build();
            RecommendContext context2 = RecommendContext.builder().size(50).build();
            RecommendContext context3 = RecommendContext.builder().size(100).build();

            assertEquals(1, context1.getSize());
            assertEquals(50, context2.getSize());
            assertEquals(100, context3.getSize());
        }

        @Test
        @DisplayName("Should allow all scenario types")
        void shouldAllowAllScenarioTypes() {
            RecommendContext daily = RecommendContext.builder().scenario(RecommendContext.Scenario.DAILY).build();
            RecommendContext similar = RecommendContext.builder().scenario(RecommendContext.Scenario.SIMILAR).build();
            RecommendContext weakPoint = RecommendContext.builder().scenario(RecommendContext.Scenario.WEAK_POINT).build();
            RecommendContext challenge = RecommendContext.builder().scenario(RecommendContext.Scenario.CHALLENGE).build();

            assertEquals(RecommendContext.Scenario.DAILY, daily.getScenario());
            assertEquals(RecommendContext.Scenario.SIMILAR, similar.getScenario());
            assertEquals(RecommendContext.Scenario.WEAK_POINT, weakPoint.getScenario());
            assertEquals(RecommendContext.Scenario.CHALLENGE, challenge.getScenario());
        }
    }
}
