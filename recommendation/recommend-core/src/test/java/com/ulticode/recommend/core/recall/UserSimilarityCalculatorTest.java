package com.ulticode.recommend.core.recall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserSimilarityCalculator Tests")
class UserSimilarityCalculatorTest {

    private final UserSimilarityCalculator calculator = new UserSimilarityCalculator();

    @Nested
    @DisplayName("Calculate Similarity Tests")
    class CalculateSimilarityTests {

        @Test
        @DisplayName("Should return 1.0 for identical problem sets")
        void shouldReturnOneForIdenticalSets() {
            Set<Long> user1Problems = Set.of(1L, 2L, 3L);
            Set<Long> user2Problems = Set.of(1L, 2L, 3L);

            double similarity = calculator.calculateSimilarity(user1Problems, user2Problems);

            assertEquals(1.0, similarity, 0.0001);
        }

        @Test
        @DisplayName("Should return 0.0 for completely different problem sets")
        void shouldReturnZeroForDisjointSets() {
            Set<Long> user1Problems = Set.of(1L, 2L, 3L);
            Set<Long> user2Problems = Set.of(4L, 5L, 6L);

            double similarity = calculator.calculateSimilarity(user1Problems, user2Problems);

            assertEquals(0.0, similarity, 0.0001);
        }

        @Test
        @DisplayName("Should correctly calculate similarity for partially overlapping sets")
        void shouldCalculatePartialOverlap() {
            // User1: {1, 2, 3, 4, 5} - 5 problems
            // User2: {1, 2, 3, 6, 7} - 5 problems
            // Intersection: {1, 2, 3} - 3 problems
            // Similarity = 3 / sqrt(5 * 5) = 3 / 5 = 0.6
            Set<Long> user1Problems = Set.of(1L, 2L, 3L, 4L, 5L);
            Set<Long> user2Problems = Set.of(1L, 2L, 3L, 6L, 7L);

            double similarity = calculator.calculateSimilarity(user1Problems, user2Problems);

            assertEquals(0.6, similarity, 0.0001);
        }

        @Test
        @DisplayName("Should calculate correct similarity for different set sizes")
        void shouldCalculateWithDifferentSizes() {
            // User1: {1, 2, 3} - 3 problems
            // User2: {1, 2, 3, 4, 5, 6, 7, 8, 9} - 9 problems
            // Intersection: {1, 2, 3} - 3 problems
            // Similarity = 3 / sqrt(3 * 9) = 3 / sqrt(27) = 3 / 5.196 = 0.577
            Set<Long> user1Problems = Set.of(1L, 2L, 3L);
            Set<Long> user2Problems = Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);

            double similarity = calculator.calculateSimilarity(user1Problems, user2Problems);

            assertEquals(3.0 / Math.sqrt(27.0), similarity, 0.001);
        }

        @Test
        @DisplayName("Should return 0.5 for 50% overlap with same size")
        void shouldCalculateHalfOverlap() {
            // User1: {1, 2, 3, 4} - 4 problems
            // User2: {1, 2, 5, 6} - 4 problems
            // Intersection: {1, 2} - 2 problems
            // Similarity = 2 / sqrt(4 * 4) = 2 / 4 = 0.5
            Set<Long> user1Problems = Set.of(1L, 2L, 3L, 4L);
            Set<Long> user2Problems = Set.of(1L, 2L, 5L, 6L);

            double similarity = calculator.calculateSimilarity(user1Problems, user2Problems);

            assertEquals(0.5, similarity, 0.0001);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should return 0.0 for null first set")
        void shouldReturnZeroForNullFirstSet() {
            Set<Long> user2Problems = Set.of(1L, 2L, 3L);

            double similarity = calculator.calculateSimilarity(null, user2Problems);

            assertEquals(0.0, similarity, 0.0001);
        }

        @Test
        @DisplayName("Should return 0.0 for null second set")
        void shouldReturnZeroForNullSecondSet() {
            Set<Long> user1Problems = Set.of(1L, 2L, 3L);

            double similarity = calculator.calculateSimilarity(user1Problems, null);

            assertEquals(0.0, similarity, 0.0001);
        }

        @Test
        @DisplayName("Should return 0.0 for both null sets")
        void shouldReturnZeroForBothNullSets() {
            double similarity = calculator.calculateSimilarity(null, null);

            assertEquals(0.0, similarity, 0.0001);
        }

        @Test
        @DisplayName("Should return 0.0 for empty first set")
        void shouldReturnZeroForEmptyFirstSet() {
            Set<Long> user1Problems = Set.of();
            Set<Long> user2Problems = Set.of(1L, 2L, 3L);

            double similarity = calculator.calculateSimilarity(user1Problems, user2Problems);

            assertEquals(0.0, similarity, 0.0001);
        }

        @Test
        @DisplayName("Should return 0.0 for empty second set")
        void shouldReturnZeroForEmptySecondSet() {
            Set<Long> user1Problems = Set.of(1L, 2L, 3L);
            Set<Long> user2Problems = Set.of();

            double similarity = calculator.calculateSimilarity(user1Problems, user2Problems);

            assertEquals(0.0, similarity, 0.0001);
        }

        @Test
        @DisplayName("Should return 0.0 for both empty sets")
        void shouldReturnZeroForBothEmptySets() {
            Set<Long> user1Problems = Set.of();
            Set<Long> user2Problems = Set.of();

            double similarity = calculator.calculateSimilarity(user1Problems, user2Problems);

            assertEquals(0.0, similarity, 0.0001);
        }

        @Test
        @DisplayName("Should handle single element sets with overlap")
        void shouldHandleSingleElementWithOverlap() {
            Set<Long> user1Problems = Set.of(1L);
            Set<Long> user2Problems = Set.of(1L);

            double similarity = calculator.calculateSimilarity(user1Problems, user2Problems);

            assertEquals(1.0, similarity, 0.0001);
        }

        @Test
        @DisplayName("Should handle single element sets without overlap")
        void shouldHandleSingleElementWithoutOverlap() {
            Set<Long> user1Problems = Set.of(1L);
            Set<Long> user2Problems = Set.of(2L);

            double similarity = calculator.calculateSimilarity(user1Problems, user2Problems);

            assertEquals(0.0, similarity, 0.0001);
        }
    }

    @Nested
    @DisplayName("Symmetry Tests")
    class SymmetryTests {

        @Test
        @DisplayName("Similarity should be symmetric")
        void shouldBeSymmetric() {
            Set<Long> user1Problems = Set.of(1L, 2L, 3L, 4L);
            Set<Long> user2Problems = Set.of(2L, 3L, 4L, 5L, 6L);

            double similarity1to2 = calculator.calculateSimilarity(user1Problems, user2Problems);
            double similarity2to1 = calculator.calculateSimilarity(user2Problems, user1Problems);

            assertEquals(similarity1to2, similarity2to1, 0.0001);
        }

        @Test
        @DisplayName("Similarity should be symmetric for different sizes")
        void shouldBeSymmetricForDifferentSizes() {
            Set<Long> user1Problems = Set.of(1L, 2L, 3L);
            Set<Long> user2Problems = Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

            double similarity1to2 = calculator.calculateSimilarity(user1Problems, user2Problems);
            double similarity2to1 = calculator.calculateSimilarity(user2Problems, user1Problems);

            assertEquals(similarity1to2, similarity2to1, 0.0001);
        }
    }

    @Nested
    @DisplayName("Range Tests")
    class RangeTests {

        @Test
        @DisplayName("Similarity should always be between 0 and 1")
        void shouldBeBetweenZeroAndOne() {
            Set<Long> user1Problems = Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);
            Set<Long> user2Problems = Set.of(2L, 3L, 5L, 7L, 11L, 13L, 17L, 19L);

            double similarity = calculator.calculateSimilarity(user1Problems, user2Problems);

            assertTrue(similarity >= 0.0 && similarity <= 1.0);
        }
    }
}
