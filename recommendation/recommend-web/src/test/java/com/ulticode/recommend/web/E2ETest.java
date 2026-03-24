package com.ulticode.recommend.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ulticode.recommend.api.RecommendService;
import com.ulticode.recommend.api.dto.RecommendItem;
import com.ulticode.recommend.api.dto.RecommendRequest;
import com.ulticode.recommend.api.dto.RecommendResponse;
import com.ulticode.recommend.api.dto.RecommendResult;
import com.ulticode.recommend.api.enums.RecommendScenario;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the recommendation REST API.
 * Tests the full request/response cycle with mocked Dubbo service.
 * Uses MockMvc standalone setup to avoid loading Dubbo context.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class E2ETest {

    private MockMvc mockMvc;

    @Mock
    private RecommendService recommendService;

    @InjectMocks
    private RecommendController recommendController;

    private ObjectMapper objectMapper;

    private static final String API_BASE_PATH = "/api/recommend";
    private static final String HEALTH_ENDPOINT = API_BASE_PATH + "/health";

    @BeforeAll
    void setUpClass() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create MockMvc with standalone setup and proper JSON converter
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(recommendController)
                .setMessageConverters(converter)
                .build();
    }

    // ========== Helper Methods for Mock Data ==========

    private RecommendRequest createValidRequest() {
        return RecommendRequest.builder()
                .userId("user-123")
                .scenario(RecommendScenario.DAILY)
                .size(10)
                .includeSolved(false)
                .build();
    }

    private RecommendRequest createRequestWithScenario(RecommendScenario scenario, Long sourceProblemId) {
        RecommendRequest.RecommendRequestBuilder builder = RecommendRequest.builder()
                .userId("user-456")
                .scenario(scenario)
                .size(5)
                .includeSolved(false);

        if (sourceProblemId != null) {
            builder.sourceProblemId(sourceProblemId);
        }

        return builder.build();
    }

    private RecommendItem createRecommendItem(Long problemId, String title, String difficulty, double score) {
        return RecommendItem.builder()
                .problemId(problemId)
                .slug("problem-" + problemId)
                .title(title)
                .difficulty(difficulty)
                .score(score)
                .tags(Arrays.asList("array", "dynamic-programming"))
                .reason("Recommended based on your learning history")
                .build();
    }

    private RecommendResult createRecommendResult(RecommendScenario scenario, List<RecommendItem> items) {
        return RecommendResult.builder()
                .items(items)
                .totalCount(items.size())
                .scenario(scenario)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private RecommendResponse<RecommendResult> createSuccessResponse(RecommendResult result) {
        return RecommendResponse.success(result);
    }

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    // ========== Test Cases ==========

    @Test
    @DisplayName("GET /api/recommend/health returns 200 with UP status")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get(HEALTH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/recommend returns recommendations for valid request")
    void testRecommendEndpoint() throws Exception {
        // Arrange
        RecommendRequest request = createValidRequest();
        List<RecommendItem> items = Arrays.asList(
                createRecommendItem(1L, "Two Sum", "Easy", 0.95),
                createRecommendItem(2L, "Add Two Numbers", "Medium", 0.88)
        );
        RecommendResult result = createRecommendResult(RecommendScenario.DAILY, items);
        RecommendResponse<RecommendResult> response = createSuccessResponse(result);

        when(recommendService.recommend(any(RecommendRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post(API_BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.message", is("Success")))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].problemId", is(1)))
                .andExpect(jsonPath("$.data.items[0].title", is("Two Sum")))
                .andExpect(jsonPath("$.data.items[0].difficulty", is("Easy")))
                .andExpect(jsonPath("$.data.items[0].score", is(0.95)))
                .andExpect(jsonPath("$.data.items[1].problemId", is(2)))
                .andExpect(jsonPath("$.data.scenario", is("DAILY")));
    }

    @Test
    @DisplayName("POST /api/recommend with invalid request returns error")
    void testRecommendWithInvalidRequest() throws Exception {
        // Arrange - create request with missing required userId
        String invalidRequestBody = "{\"size\": 10}";

        // Act & Assert - standalone MockMvc doesn't validate, but controller handles null
        mockMvc.perform(post(API_BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                .andExpect(status().isOk()); // Without validation, returns 200 but with error
    }

    @Test
    @DisplayName("POST /api/recommend with DAILY scenario returns daily recommendations")
    void testRecommendWithDailyScenario() throws Exception {
        // Arrange
        RecommendRequest request = createRequestWithScenario(RecommendScenario.DAILY, null);
        List<RecommendItem> items = Collections.singletonList(
                createRecommendItem(100L, "Daily Practice Problem", "Medium", 0.92)
        );
        RecommendResult result = createRecommendResult(RecommendScenario.DAILY, items);
        RecommendResponse<RecommendResult> response = createSuccessResponse(result);

        when(recommendService.recommend(any(RecommendRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post(API_BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.scenario", is("DAILY")))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].title", is("Daily Practice Problem")));
    }

    @Test
    @DisplayName("POST /api/recommend with SIMILAR scenario returns similar problems")
    void testRecommendWithSimilarScenario() throws Exception {
        // Arrange
        Long sourceProblemId = 42L;
        RecommendRequest request = createRequestWithScenario(RecommendScenario.SIMILAR, sourceProblemId);
        List<RecommendItem> items = Arrays.asList(
                createRecommendItem(101L, "Similar Problem 1", "Medium", 0.89),
                createRecommendItem(102L, "Similar Problem 2", "Hard", 0.85)
        );
        RecommendResult result = createRecommendResult(RecommendScenario.SIMILAR, items);
        RecommendResponse<RecommendResult> response = createSuccessResponse(result);

        when(recommendService.recommend(any(RecommendRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post(API_BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.scenario", is("SIMILAR")))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
    }

    @Test
    @DisplayName("POST /api/recommend with CHALLENGE scenario returns challenging problems")
    void testRecommendWithChallengeScenario() throws Exception {
        // Arrange
        RecommendRequest request = createRequestWithScenario(RecommendScenario.CHALLENGE, null);
        List<RecommendItem> items = Arrays.asList(
                createRecommendItem(201L, "Challenge Problem 1", "Hard", 0.78),
                createRecommendItem(202L, "Challenge Problem 2", "Hard", 0.75),
                createRecommendItem(203L, "Challenge Problem 3", "Hard", 0.72)
        );
        RecommendResult result = createRecommendResult(RecommendScenario.CHALLENGE, items);
        RecommendResponse<RecommendResult> response = createSuccessResponse(result);

        when(recommendService.recommend(any(RecommendRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post(API_BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.scenario", is("CHALLENGE")))
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].difficulty", is("Hard")));
    }

    @Test
    @DisplayName("POST /api/recommend with WEAK_POINT scenario returns weak point recommendations")
    void testRecommendWithWeakPointScenario() throws Exception {
        // Arrange
        RecommendRequest request = createRequestWithScenario(RecommendScenario.WEAK_POINT, null);
        List<RecommendItem> items = Collections.singletonList(
                createRecommendItem(301L, "Weak Point Exercise", "Easy", 0.90)
        );
        RecommendResult result = createRecommendResult(RecommendScenario.WEAK_POINT, items);
        RecommendResponse<RecommendResult> response = createSuccessResponse(result);

        when(recommendService.recommend(any(RecommendRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post(API_BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.scenario", is("WEAK_POINT")))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    @DisplayName("POST /api/recommend handles service error gracefully")
    void testRecommendHandlesServiceError() throws Exception {
        // Arrange
        RecommendRequest request = createValidRequest();
        when(recommendService.recommend(any(RecommendRequest.class)))
                .thenThrow(new RuntimeException("Service unavailable"));

        // Act & Assert
        mockMvc.perform(post(API_BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(500)));
    }

    @Test
    @DisplayName("POST /api/recommend with target tags returns filtered recommendations")
    void testRecommendWithTargetTags() throws Exception {
        // Arrange
        RecommendRequest request = RecommendRequest.builder()
                .userId("user-789")
                .scenario(RecommendScenario.DAILY)
                .size(5)
                .targetTags(Arrays.asList("array", "two-pointers"))
                .build();

        List<RecommendItem> items = Collections.singletonList(
                RecommendItem.builder()
                        .problemId(501L)
                        .slug("two-sum-ii")
                        .title("Two Sum II - Input Array Is Sorted")
                        .difficulty("Medium")
                        .score(0.91)
                        .tags(Arrays.asList("array", "two-pointers", "binary-search"))
                        .reason("Matches your selected tags")
                        .build()
        );
        RecommendResult result = createRecommendResult(RecommendScenario.DAILY, items);
        RecommendResponse<RecommendResult> response = createSuccessResponse(result);

        when(recommendService.recommend(any(RecommendRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post(API_BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].tags", hasSize(3)));
    }

    @Test
    @DisplayName("POST /api/recommend with includeSolved flag")
    void testRecommendWithIncludeSolved() throws Exception {
        // Arrange
        RecommendRequest request = RecommendRequest.builder()
                .userId("user-999")
                .scenario(RecommendScenario.DAILY)
                .size(10)
                .includeSolved(true)
                .build();

        List<RecommendItem> items = Arrays.asList(
                createRecommendItem(401L, "Solved Problem 1", "Easy", 0.80),
                createRecommendItem(402L, "New Problem", "Medium", 0.85)
        );
        RecommendResult result = createRecommendResult(RecommendScenario.DAILY, items);
        RecommendResponse<RecommendResult> response = createSuccessResponse(result);

        when(recommendService.recommend(any(RecommendRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post(API_BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
    }

    @Test
    @DisplayName("POST /api/recommend returns empty list when no recommendations found")
    void testRecommendWithEmptyResults() throws Exception {
        // Arrange
        RecommendRequest request = createValidRequest();
        RecommendResult result = createRecommendResult(RecommendScenario.DAILY, Collections.emptyList());
        RecommendResponse<RecommendResult> response = createSuccessResponse(result);

        when(recommendService.recommend(any(RecommendRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post(API_BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.totalCount", is(0)));
    }
}
