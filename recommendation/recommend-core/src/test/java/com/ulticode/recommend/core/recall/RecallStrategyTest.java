package com.ulticode.recommend.core.recall;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecallStrategy Tests")
class RecallStrategyTest {

    @Nested
    @DisplayName("Default Methods Tests")
    class DefaultMethodsTests {

        @Test
        @DisplayName("Should return class simple name as default name")
        void shouldReturnClassSimpleNameAsDefaultName() {
            RecallStrategy strategy = new TestRecallStrategy();
            assertEquals("TestRecallStrategy", strategy.getName());
        }

        @Test
        @DisplayName("Should return zero as default priority")
        void shouldReturnZeroAsDefaultPriority() {
            RecallStrategy strategy = new TestRecallStrategy();
            assertEquals(0, strategy.getPriority());
        }
    }

    @Nested
    @DisplayName("Lambda Implementation Tests")
    class LambdaImplementationTests {

        @Test
        @DisplayName("Should create strategy using lambda")
        void shouldCreateStrategyUsingLambda() {
            RecallStrategy lambdaStrategy = (context, profile) -> List.of(
                    RecommendItem.builder()
                            .problemId(1L)
                            .slug("two-sum")
                            .title("Two Sum")
                            .build()
            );

            RecommendContext context = RecommendContext.builder()
                    .userId("user123")
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user123")
                    .build();

            List<RecommendItem> items = lambdaStrategy.recall(context, profile);

            assertNotNull(items);
            assertEquals(1, items.size());
            assertEquals(1L, items.get(0).getProblemId());
            assertEquals("two-sum", items.get(0).getSlug());
        }

        @Test
        @DisplayName("Should use default methods with lambda implementation")
        void shouldUseDefaultMethodsWithLambdaImplementation() {
            RecallStrategy lambdaStrategy = (context, profile) -> List.of();

            // Lambda uses the default methods from the interface
            assertTrue(lambdaStrategy.getName().contains("$Lambda$"));
            assertEquals(0, lambdaStrategy.getPriority());
        }
    }

    @Nested
    @DisplayName("Simple Implementation Tests")
    class SimpleImplementationTests {

        @Test
        @DisplayName("Should recall items using simple implementation")
        void shouldRecallItemsUsingSimpleImplementation() {
            SimpleRecallStrategy strategy = new SimpleRecallStrategy();
            RecommendContext context = RecommendContext.builder()
                    .userId("user123")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user123")
                    .rating(1500)
                    .build();

            List<RecommendItem> items = strategy.recall(context, profile);

            assertNotNull(items);
            assertEquals(3, items.size());
            assertTrue(items.stream().allMatch(item -> item.getProblemId() != null));
        }

        @Test
        @DisplayName("Should return custom name from overridden getName")
        void shouldReturnCustomNameFromOverriddenGetName() {
            SimpleRecallStrategy strategy = new SimpleRecallStrategy();
            assertEquals("SimpleRecallStrategy", strategy.getName());
        }

        @Test
        @DisplayName("Should return custom priority from overridden getPriority")
        void shouldReturnCustomPriorityFromOverriddenGetPriority() {
            SimpleRecallStrategy strategy = new SimpleRecallStrategy();
            assertEquals(10, strategy.getPriority());
        }
    }

    @Nested
    @DisplayName("Interface Contract Tests")
    class InterfaceContractTests {

        @Test
        @DisplayName("Should allow returning empty list")
        void shouldAllowReturningEmptyList() {
            RecallStrategy emptyStrategy = (context, profile) -> List.of();

            RecommendContext context = RecommendContext.builder().build();
            UserProfile profile = UserProfile.builder().build();

            List<RecommendItem> items = emptyStrategy.recall(context, profile);

            assertNotNull(items);
            assertTrue(items.isEmpty());
        }

        @Test
        @DisplayName("Should accept null context and profile")
        void shouldAcceptNullContextAndProfile() {
            RecallStrategy nullSafeStrategy = (context, profile) -> {
                if (context == null || profile == null) {
                    return List.of();
                }
                return List.of(
                        RecommendItem.builder().problemId(1L).build()
                );
            };

            List<RecommendItem> items = nullSafeStrategy.recall(null, null);

            assertNotNull(items);
            assertTrue(items.isEmpty());
        }

        @Test
        @DisplayName("Should allow multiple strategies with different priorities")
        void shouldAllowMultipleStrategiesWithDifferentPriorities() {
            RecallStrategy highPriority = new HighPriorityStrategy();
            RecallStrategy lowPriority = new LowPriorityStrategy();

            assertTrue(highPriority.getPriority() > lowPriority.getPriority());
        }
    }

    // Test helper classes

    /**
     * Basic test implementation of RecallStrategy
     */
    static class TestRecallStrategy implements RecallStrategy {
        @Override
        public List<RecommendItem> recall(RecommendContext context, UserProfile profile) {
            return List.of();
        }
    }

    /**
     * Simple implementation with custom priority
     */
    static class SimpleRecallStrategy implements RecallStrategy {
        @Override
        public List<RecommendItem> recall(RecommendContext context, UserProfile profile) {
            return List.of(
                    RecommendItem.builder()
                            .problemId(1L)
                            .slug("two-sum")
                            .title("Two Sum")
                            .difficulty("Easy")
                            .build(),
                    RecommendItem.builder()
                            .problemId(2L)
                            .slug("add-two-numbers")
                            .title("Add Two Numbers")
                            .difficulty("Medium")
                            .build(),
                    RecommendItem.builder()
                            .problemId(3L)
                            .slug("median-of-two-sorted-arrays")
                            .title("Median of Two Sorted Arrays")
                            .difficulty("Hard")
                            .build()
            );
        }

        @Override
        public int getPriority() {
            return 10;
        }
    }

    /**
     * High priority strategy
     */
    static class HighPriorityStrategy implements RecallStrategy {
        @Override
        public List<RecommendItem> recall(RecommendContext context, UserProfile profile) {
            return List.of();
        }

        @Override
        public int getPriority() {
            return 100;
        }
    }

    /**
     * Low priority strategy
     */
    static class LowPriorityStrategy implements RecallStrategy {
        @Override
        public List<RecommendItem> recall(RecommendContext context, UserProfile profile) {
            return List.of();
        }

        @Override
        public int getPriority() {
            return 1;
        }
    }
}
