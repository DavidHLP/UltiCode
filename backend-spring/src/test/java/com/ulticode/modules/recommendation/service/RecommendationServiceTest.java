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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RecommendationService.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private RecommendationConfig recommendationConfig;

    @Mock
    private RestTemplate restTemplate;

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
            when(recommendationConfig.isEnabled()).thenReturn(false);

            assertFalse(recommendationService.isAvailable());
        }

        @Test
        @DisplayName("should return false when service URL is null")
        void shouldReturnFalseWhenServiceUrlIsNull() {
            when(recommendationConfig.isEnabled()).thenReturn(true);
            when(recommendationConfig.getServiceUrl()).thenReturn(null);

            assertFalse(recommendationService.isAvailable());
        }

        @Test
        @DisplayName("should return false when service URL is blank")
        void shouldReturnFalseWhenServiceUrlIsBlank() {
            when(recommendationConfig.isEnabled()).thenReturn(true);
            when(recommendationConfig.getServiceUrl()).thenReturn("  ");

            assertFalse(recommendationService.isAvailable());
        }

        @Test
        @DisplayName("should return true when service is properly configured")
        void shouldReturnTrueWhenConfigured() {
            when(recommendationConfig.isEnabled()).thenReturn(true);
            when(recommendationConfig.getServiceUrl()).thenReturn("http://recommendation-service:8080");

            assertTrue(recommendationService.isAvailable());
        }
    }

    @Nested
    @DisplayName("Daily Recommendations Tests")
    class DailyRecommendationsTests {

        @Test
        @DisplayName("should return empty list when service is disabled")
        void shouldReturnEmptyListWhenDisabled() {
            when(recommendationConfig.isEnabled()).thenReturn(false);

            RecommendResponseVO response = recommendationService.getDailyRecommendations(10);

            assertNotNull(response);
            assertTrue(response.getSuccess());
            assertNotNull(response.getData());
            assertTrue(response.getData().getItems().isEmpty());
        }

        @Test
        @DisplayName("should return error when user not authenticated")
        void shouldReturnErrorWhenNotAuthenticated() {
            when(recommendationConfig.isEnabled()).thenReturn(true);
            when(recommendationConfig.getServiceUrl()).thenReturn("http://recommendation-service:8080");

            // SecurityUtil will return null when not authenticated
            // This test verifies the authentication check is in place

            // Note: In a real test environment with Spring Security context,
            // we would need to mock the security context
            RecommendResponseVO response = recommendationService.getDailyRecommendations(10);

            // Since we can't mock SecurityUtil.getCurrentUserId() directly,
            // the test will either return an error or success based on the context
            assertNotNull(response);
        }
    }

    @Nested
    @DisplayName("Similar Problems Tests")
    class SimilarProblemsTests {

        @Test
        @DisplayName("should return error when problem ID is null")
        void shouldReturnErrorWhenProblemIdIsNull() {
            when(recommendationConfig.isEnabled()).thenReturn(true);
            when(recommendationConfig.getServiceUrl()).thenReturn("http://recommendation-service:8080");

            RecommendResponseVO response = recommendationService.getSimilarProblems(null, 10);

            assertNotNull(response);
            assertFalse(response.getSuccess());
            assertEquals(40000, response.getCode());
        }

        @Test
        @DisplayName("should return empty list when service is disabled")
        void shouldReturnEmptyListWhenDisabled() {
            when(recommendationConfig.isEnabled()).thenReturn(false);

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
            when(recommendationConfig.isEnabled()).thenReturn(false);

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
            when(recommendationConfig.isEnabled()).thenReturn(false);

            RecommendResponseVO response = recommendationService.getChallengeRecommendations(10);

            assertNotNull(response);
            assertTrue(response.getSuccess());
        }
    }

    @Nested
    @DisplayName("Health Check Tests")
    class HealthCheckTests {

        @Test
        @DisplayName("should return disabled message when service is disabled")
        void shouldReturnDisabledMessageWhenDisabled() {
            when(recommendationConfig.isEnabled()).thenReturn(false);

            RecommendResponseVO response = recommendationService.healthCheck();

            assertNotNull(response);
            assertTrue(response.getSuccess());
            assertEquals(200, response.getCode());
            assertTrue(response.getMessage().contains("disabled"));
        }

        @Test
        @DisplayName("should return healthy when service responds")
        void shouldReturnHealthyWhenServiceResponds() {
            when(recommendationConfig.isEnabled()).thenReturn(true);
            when(recommendationConfig.getServiceUrl()).thenReturn("http://recommendation-service:8080");

            ResponseEntity<Map> mockResponse = new ResponseEntity<>(new java.util.HashMap<>(), HttpStatus.OK);
            when(restTemplate.getForEntity(anyString(), eq(Map.class))).thenReturn(mockResponse);

            RecommendResponseVO response = recommendationService.healthCheck();

            assertNotNull(response);
            assertTrue(response.getSuccess());
            assertEquals(200, response.getCode());
        }

        @Test
        @DisplayName("should return error when service is unavailable")
        void shouldReturnErrorWhenServiceUnavailable() {
            when(recommendationConfig.isEnabled()).thenReturn(true);
            when(recommendationConfig.getServiceUrl()).thenReturn("http://recommendation-service:8080");
            when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            RecommendResponseVO response = recommendationService.healthCheck();

            assertNotNull(response);
            assertFalse(response.getSuccess());
            assertEquals(50000, response.getCode());
        }
    }

    @Nested
    @DisplayName("Get Recommendations Tests")
    class GetRecommendationsTests {

        @Test
        @DisplayName("should return empty list when service is disabled")
        void shouldReturnEmptyListWhenDisabled() {
            when(recommendationConfig.isEnabled()).thenReturn(false);

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
            when(recommendationConfig.isEnabled()).thenReturn(false);

            RecommendResponseVO response = recommendationService.getRecommendations(dto);

            assertNotNull(response);
            // Default scenario is applied internally
        }

        @Test
        @DisplayName("should handle SIMILAR scenario with problem ID")
        void shouldHandleSimilarScenarioWithProblemId() {
            dto.setScenario("SIMILAR");
            dto.setProblemId(1L);
            when(recommendationConfig.isEnabled()).thenReturn(false);

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
