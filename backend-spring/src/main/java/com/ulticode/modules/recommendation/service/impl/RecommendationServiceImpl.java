package com.ulticode.modules.recommendation.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.recommendation.dto.GetRecommendationsDTO;
import com.ulticode.modules.recommendation.dto.RecommendResponseVO;
import com.ulticode.modules.recommendation.dto.RecommendResponseVO.RecommendItem;
import com.ulticode.modules.recommendation.service.RecommendationService;
import com.ulticode.recommend.api.RecommendService;
import com.ulticode.recommend.api.dto.RecommendRequest;
import com.ulticode.recommend.api.dto.RecommendResponse;
import com.ulticode.recommend.api.dto.RecommendResult;
import com.ulticode.recommend.api.enums.RecommendScenario;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Implementation of RecommendationService using Dubbo RPC.
 * Directly calls the recommendation provider service via Dubbo,
 * eliminating the HTTP → Dubbo protocol translation layer.
 */
@Slf4j
@Service
public class RecommendationServiceImpl implements RecommendationService {

    @DubboReference(check = false, timeout = 5000, retries = 1)
    private RecommendService recommendService;

    @Value("${recommendation.enabled:false}")
    private boolean enabled;

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    @Override
    public RecommendResponseVO getRecommendations(GetRecommendationsDTO dto) {
        if (!enabled) return createDisabledResponse();

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return RecommendResponseVO.error(40100, "User not authenticated");
        }

        RecommendScenario scenario = parseScenario(dto.getScenario());
        return callDubboService(userId, scenario, dto.getLimit(), dto.getProblemId());
    }

    @Override
    public RecommendResponseVO getDailyRecommendations(Integer limit) {
        if (!enabled) return createDisabledResponse();

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return RecommendResponseVO.error(40100, "User not authenticated");
        }

        return callDubboService(userId, RecommendScenario.DAILY, limit, null);
    }

    @Override
    public RecommendResponseVO getSimilarProblems(Long problemId, Integer limit) {
        if (!enabled) return createDisabledResponse();

        if (problemId == null) {
            return RecommendResponseVO.error(40000, "Problem ID is required");
        }

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return RecommendResponseVO.error(40100, "User not authenticated");
        }

        return callDubboService(userId, RecommendScenario.SIMILAR, limit, problemId);
    }

    @Override
    public RecommendResponseVO getWeakPointRecommendations(Integer limit) {
        if (!enabled) return createDisabledResponse();

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return RecommendResponseVO.error(40100, "User not authenticated");
        }

        return callDubboService(userId, RecommendScenario.WEAK_POINT, limit, null);
    }

    @Override
    public RecommendResponseVO getChallengeRecommendations(Integer limit) {
        if (!enabled) return createDisabledResponse();

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return RecommendResponseVO.error(40100, "User not authenticated");
        }

        return callDubboService(userId, RecommendScenario.CHALLENGE, limit, null);
    }

    @Override
    public RecommendResponseVO healthCheck() {
        if (!enabled) {
            RecommendResponseVO response = new RecommendResponseVO();
            response.setSuccess(true);
            response.setCode(200);
            response.setMessage("Recommendation service is disabled");
            return response;
        }

        try {
            // Ping the Dubbo service with a minimal request
            RecommendRequest request = RecommendRequest.builder()
                    .userId("health-check")
                    .scenario(RecommendScenario.DAILY)
                    .size(1)
                    .build();
            RecommendResponse<RecommendResult> result = recommendService.recommend(request);

            RecommendResponseVO response = new RecommendResponseVO();
            response.setSuccess(result.isSuccess());
            response.setCode(result.getCode());
            response.setMessage("Recommendation service is healthy");
            return response;
        } catch (Exception e) {
            log.warn("Recommendation service health check failed: {}", e.getMessage());
            return RecommendResponseVO.error(50000, "Recommendation service unavailable: " + e.getMessage());
        }
    }

    /**
     * Calls the Dubbo recommendation service and converts the result.
     */
    private RecommendResponseVO callDubboService(String userId, RecommendScenario scenario,
                                                  Integer limit, Long problemId) {
        try {
            RecommendRequest request = RecommendRequest.builder()
                    .userId(userId)
                    .scenario(scenario)
                    .size(limit != null ? limit : 10)
                    .sourceProblemId(problemId)
                    .build();

            log.debug("Calling recommendation service via Dubbo: userId={}, scenario={}", userId, scenario);
            RecommendResponse<RecommendResult> response = recommendService.recommend(request);

            if (response.isSuccess() && response.getData() != null) {
                List<RecommendItem> items = response.getData().getItems().stream()
                        .map(this::convertApiItemToVoItem)
                        .collect(Collectors.toList());

                return RecommendResponseVO.success(items);
            } else {
                log.warn("Recommendation service returned error: code={}, message={}",
                        response.getCode(), response.getMessage());
                return RecommendResponseVO.error(response.getCode(), response.getMessage());
            }
        } catch (Exception e) {
            log.error("Dubbo recommendation service call failed: {}", e.getMessage());
            return RecommendResponseVO.error(50000, "Recommendation service unavailable: " + e.getMessage());
        }
    }

    /**
     * Converts a Dubbo API RecommendItem to the VO's inner RecommendItem.
     */
    private RecommendItem convertApiItemToVoItem(com.ulticode.recommend.api.dto.RecommendItem apiItem) {
        RecommendItem voItem = new RecommendItem();
        voItem.setProblemId(apiItem.getProblemId());
        voItem.setTitle(apiItem.getTitle());
        voItem.setSlug(apiItem.getSlug());
        voItem.setDifficulty(apiItem.getDifficulty());
        voItem.setScore((float) apiItem.getScore());
        voItem.setReason(apiItem.getReason());
        voItem.setTags(apiItem.getTags());
        return voItem;
    }

    private RecommendScenario parseScenario(String scenario) {
        if (scenario == null) return RecommendScenario.DAILY;
        return switch (scenario.toUpperCase()) {
            case "SIMILAR" -> RecommendScenario.SIMILAR;
            case "WEAK_POINT" -> RecommendScenario.WEAK_POINT;
            case "CHALLENGE" -> RecommendScenario.CHALLENGE;
            default -> RecommendScenario.DAILY;
        };
    }

    private RecommendResponseVO createDisabledResponse() {
        return RecommendResponseVO.success(new ArrayList<>());
    }
}
