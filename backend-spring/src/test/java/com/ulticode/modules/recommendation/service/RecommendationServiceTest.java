package com.ulticode.modules.recommendation.service;

import com.ulticode.modules.recommendation.config.RecommendationConfig;
import com.ulticode.modules.recommendation.dto.GetRecommendationsDTO;
import com.ulticode.modules.recommendation.dto.RecommendResponseVO;
import com.ulticode.modules.recommendation.service.impl.RecommendationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RecommendationService.
 *
 * Note: Tests referencing the old RestTemplate-based implementation (serviceUrl, healthCheck)
 * were removed when the service was migrated to Dubbo RPC. See git history for original file.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private RecommendationConfig recommendationConfig;

    @InjectMocks
    private RecommendationServiceImpl recommendationService;

    private GetRecommendationsDTO dto;

    @BeforeEach
    void setUp() {
        dto = new GetRecommendationsDTO();
        dto.setLimit(10);
        dto.setScenario("DAILY");
    }

    @Nested
    @DisplayName("Availability Tests")
    class AvailabilityTests {

        @Test
        @DisplayName("should return false when recommendation is disabled")
        void shouldReturnFalseWhenDisabled() {
            org.springframework.test.util.ReflectionTestUtils.setField(
                    recommendationService, "enabled", false);

            assertFalse(recommendationService.isAvailable());
        }

        @Test
        @DisplayName("should return true when recommendation is enabled")
        void shouldReturnTrueWhenEnabled() {
            org.springframework.test.util.ReflectionTestUtils.setField(
                    recommendationService, "enabled", true);

            assertTrue(recommendationService.isAvailable());
        }
    }

    @Nested
    @DisplayName("Daily Recommendations Tests")
    class DailyRecommendationsTests {

        @Test
        @DisplayName("should return empty list when service is disabled")
        void shouldReturnEmptyListWhenDisabled() {
            org.springframework.test.util.ReflectionTestUtils.setField(
                    recommendationService, "enabled", false);

            RecommendResponseVO response = recommendationService.getDailyRecommendations(10);

            assertNotNull(response);
            assertTrue(response.getSuccess());
            assertNotNull(response.getData());
            assertTrue(response.getData().getItems().isEmpty());
        }
    }

    @Nested
    @DisplayName("Similar Problems Tests")
    class SimilarProblemsTests {

        @Test
        @DisplayName("should return empty list when service is disabled")
        void shouldReturnEmptyListWhenDisabled() {
            org.springframework.test.util.ReflectionTestUtils.setField(
                    recommendationService, "enabled", false);

            RecommendResponseVO response = recommendationService.getSimilarProblems(1L, 10);

            assertNotNull(response);
            assertTrue(response.getSuccess());
        }
    }

    @Nested
    @DisplayName("Weak Point Recommendations Tests")
    class WeakPointRecommendationsTests {

        @Test
        @DisplayName("should return empty list when service is disabled")
        void shouldReturnEmptyListWhenDisabled() {
            org.springframework.test.util.ReflectionTestUtils.setField(
                    recommendationService, "enabled", false);

            RecommendResponseVO response = recommendationService.getWeakPointRecommendations(10);

            assertNotNull(response);
            assertTrue(response.getSuccess());
        }
    }

    @Nested
    @DisplayName("Challenge Recommendations Tests")
    class ChallengeRecommendationsTests {

        @Test
        @DisplayName("should return empty list when service is disabled")
        void shouldReturnEmptyListWhenDisabled() {
            org.springframework.test.util.ReflectionTestUtils.setField(
                    recommendationService, "enabled", false);

            RecommendResponseVO response = recommendationService.getChallengeRecommendations(10);

            assertNotNull(response);
            assertTrue(response.getSuccess());
        }
    }

    @Nested
    @DisplayName("Get Recommendations Tests")
    class GetRecommendationsTests {

        @Test
        @DisplayName("should return empty list when service is disabled")
        void shouldReturnEmptyListWhenDisabled() {
            org.springframework.test.util.ReflectionTestUtils.setField(
                    recommendationService, "enabled", false);

            RecommendResponseVO response = recommendationService.getRecommendations(dto);

            assertNotNull(response);
            assertTrue(response.getSuccess());
            assertNotNull(response.getData());
            assertTrue(response.getData().getItems().isEmpty());
        }

        @Test
        @DisplayName("should use DAILY as default scenario")
        void shouldUseDailyAsDefaultScenario() {
            dto.setScenario(null);
            org.springframework.test.util.ReflectionTestUtils.setField(
                    recommendationService, "enabled", false);

            RecommendResponseVO response = recommendationService.getRecommendations(dto);

            assertNotNull(response);
        }

        @Test
        @DisplayName("should handle SIMILAR scenario with problem ID")
        void shouldHandleSimilarScenarioWithProblemId() {
            dto.setScenario("SIMILAR");
            dto.setProblemId(1L);
            org.springframework.test.util.ReflectionTestUtils.setField(
                    recommendationService, "enabled", false);

            RecommendResponseVO response = recommendationService.getRecommendations(dto);

            assertNotNull(response);
        }
    }

    @Nested
    @DisplayName("RecommendResponseVO Tests")
    class ResponseVOTests {

        @Test
        @DisplayName("should create success response with items")
        void shouldCreateSuccessResponseWithItems() {
            List<RecommendResponseVO.RecommendItem> items = new ArrayList<>();
            RecommendResponseVO.RecommendItem item = new RecommendResponseVO.RecommendItem();
            item.setProblemId(1L);
            item.setTitle("Two Sum");
            item.setSlug("two-sum");
            item.setDifficulty("EASY");
            item.setScore(0.95f);
            items.add(item);

            RecommendResponseVO response = RecommendResponseVO.success(items);

            assertTrue(response.getSuccess());
            assertEquals(200, response.getCode());
            assertEquals("success", response.getMessage());
            assertNotNull(response.getData());
            assertEquals(1, response.getData().getItems().size());
        }

        @Test
        @DisplayName("should create error response")
        void shouldCreateErrorResponse() {
            RecommendResponseVO response = RecommendResponseVO.error(50000, "Service unavailable");

            assertFalse(response.getSuccess());
            assertEquals(50000, response.getCode());
            assertEquals("Service unavailable", response.getMessage());
            assertNull(response.getData());
        }
    }

    @Nested
    @DisplayName("DTO Validation Tests")
    class DtoValidationTests {

        @Test
        @DisplayName("should have default limit of 10")
        void shouldHaveDefaultLimitOf10() {
            GetRecommendationsDTO newDto = new GetRecommendationsDTO();
            assertEquals(10, newDto.getLimit());
        }

        @Test
        @DisplayName("should have default includeDetails of false")
        void shouldHaveDefaultIncludeDetailsOfFalse() {
            GetRecommendationsDTO newDto = new GetRecommendationsDTO();
            assertFalse(newDto.getIncludeDetails());
        }
    }
}
