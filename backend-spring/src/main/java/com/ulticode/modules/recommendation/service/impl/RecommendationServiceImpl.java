package com.ulticode.modules.recommendation.service.impl;

import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.recommendation.config.RecommendationConfig;
import com.ulticode.modules.recommendation.dto.GetRecommendationsDTO;
import com.ulticode.modules.recommendation.dto.RecommendResponseVO;
import com.ulticode.modules.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of RecommendationService.
 * Calls an external recommendation microservice via RestTemplate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final RecommendationConfig recommendationConfig;
    private final RestTemplate restTemplate;

    @Override
    public boolean isAvailable() {
        if (!recommendationConfig.isEnabled()) {
            return false;
        }
        String serviceUrl = recommendationConfig.getServiceUrl();
        return serviceUrl != null && !serviceUrl.isBlank();
    }

    @Override
    public RecommendResponseVO getRecommendations(GetRecommendationsDTO dto) {
        if (!isAvailable()) {
            return createDisabledResponse();
        }

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return RecommendResponseVO.error(40100, "User not authenticated");
        }

        String scenario = dto.getScenario() != null ? dto.getScenario() : "DAILY";
        String endpoint = buildEndpoint(scenario, dto.getProblemId());

        return callRecommendationService(endpoint, buildRequestParams(userId, dto));
    }

    @Override
    public RecommendResponseVO getDailyRecommendations(Integer limit) {
        if (!isAvailable()) {
            return createDisabledResponse();
        }

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return RecommendResponseVO.error(40100, "User not authenticated");
        }

        GetRecommendationsDTO dto = new GetRecommendationsDTO();
        dto.setLimit(limit != null ? limit : 10);
        dto.setScenario("DAILY");

        return callRecommendationService("/recommend/daily", buildRequestParams(userId, dto));
    }

    @Override
    public RecommendResponseVO getSimilarProblems(Long problemId, Integer limit) {
        if (!isAvailable()) {
            return createDisabledResponse();
        }

        if (problemId == null) {
            return RecommendResponseVO.error(40000, "Problem ID is required");
        }

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return RecommendResponseVO.error(40100, "User not authenticated");
        }

        GetRecommendationsDTO dto = new GetRecommendationsDTO();
        dto.setLimit(limit != null ? limit : 10);
        dto.setProblemId(problemId);

        return callRecommendationService(
                "/recommend/similar/" + problemId,
                buildRequestParams(userId, dto)
        );
    }

    @Override
    public RecommendResponseVO getWeakPointRecommendations(Integer limit) {
        if (!isAvailable()) {
            return createDisabledResponse();
        }

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return RecommendResponseVO.error(40100, "User not authenticated");
        }

        GetRecommendationsDTO dto = new GetRecommendationsDTO();
        dto.setLimit(limit != null ? limit : 10);
        dto.setScenario("WEAK_POINT");

        return callRecommendationService("/recommend/weak-points", buildRequestParams(userId, dto));
    }

    @Override
    public RecommendResponseVO getChallengeRecommendations(Integer limit) {
        if (!isAvailable()) {
            return createDisabledResponse();
        }

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return RecommendResponseVO.error(40100, "User not authenticated");
        }

        GetRecommendationsDTO dto = new GetRecommendationsDTO();
        dto.setLimit(limit != null ? limit : 10);
        dto.setScenario("CHALLENGE");

        return callRecommendationService("/recommend/challenge", buildRequestParams(userId, dto));
    }

    @Override
    public RecommendResponseVO healthCheck() {
        if (!isAvailable()) {
            RecommendResponseVO response = new RecommendResponseVO();
            response.setSuccess(true);
            response.setCode(200);
            response.setMessage("Recommendation service is disabled");
            return response;
        }

        try {
            String healthUrl = recommendationConfig.getServiceUrl() + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                RecommendResponseVO result = new RecommendResponseVO();
                result.setSuccess(true);
                result.setCode(200);
                result.setMessage("Recommendation service is healthy");
                return result;
            } else {
                return RecommendResponseVO.error(50000, "Recommendation service unhealthy");
            }
        } catch (RestClientException e) {
            log.warn("Recommendation service health check failed: {}", e.getMessage());
            return RecommendResponseVO.error(50000, "Recommendation service unavailable: " + e.getMessage());
        }
    }

    /**
     * Build the endpoint URL based on scenario.
     */
    private String buildEndpoint(String scenario, Long problemId) {
        return switch (scenario.toUpperCase()) {
            case "DAILY" -> "/recommend/daily";
            case "SIMILAR" -> "/recommend/similar/" + problemId;
            case "WEAK_POINT" -> "/recommend/weak-points";
            case "CHALLENGE" -> "/recommend/challenge";
            default -> "/recommend/daily";
        };
    }

    /**
     * Build request parameters map.
     */
    private Map<String, Object> buildRequestParams(String userId, GetRecommendationsDTO dto) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("limit", dto.getLimit());
        if (dto.getProblemId() != null) {
            params.put("problemId", dto.getProblemId());
        }
        return params;
    }

    /**
     * Call the external recommendation service.
     */
    private RecommendResponseVO callRecommendationService(String endpoint, Map<String, Object> params) {
        String serviceUrl = recommendationConfig.getServiceUrl();
        String url = serviceUrl + endpoint;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            // Add user context for the microservice
            String userId = (String) params.get("userId");
            if (userId != null) {
                headers.set("X-User-Id", userId);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, headers);

            log.debug("Calling recommendation service: {}", url);
            ResponseEntity<RecommendResponseVO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    RecommendResponseVO.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                log.warn("Recommendation service returned non-success status: {}", response.getStatusCode());
                return RecommendResponseVO.error(50000, "Recommendation service error");
            }
        } catch (RestClientException e) {
            log.error("Failed to call recommendation service: {}", e.getMessage());

            // Try fallback URL if available
            String fallbackUrl = recommendationConfig.getFallbackUrl();
            if (fallbackUrl != null && !fallbackUrl.isBlank()) {
                return tryFallbackService(fallbackUrl + endpoint, params);
            }

            return RecommendResponseVO.error(50000, "Recommendation service unavailable: " + e.getMessage());
        }
    }

    /**
     * Try the fallback recommendation service.
     */
    private RecommendResponseVO tryFallbackService(String url, Map<String, Object> params) {
        try {
            log.info("Trying fallback recommendation service: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            String userId = (String) params.get("userId");
            if (userId != null) {
                headers.set("X-User-Id", userId);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, headers);

            ResponseEntity<RecommendResponseVO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    RecommendResponseVO.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (RestClientException e) {
            log.error("Fallback recommendation service also failed: {}", e.getMessage());
        }

        return RecommendResponseVO.error(50000, "All recommendation services unavailable");
    }

    /**
     * Create a response for when the service is disabled.
     */
    private RecommendResponseVO createDisabledResponse() {
        return RecommendResponseVO.success(new ArrayList<>());
    }
}
