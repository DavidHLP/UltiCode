package com.ulticode.modules.recommendation.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.recommendation.dto.GetRecommendationsDTO;
import com.ulticode.modules.recommendation.dto.RecommendResponseVO;
import com.ulticode.modules.recommendation.dto.RecommendResponseVO.RecommendItem;
import com.ulticode.modules.recommendation.service.RecommendationService;
import com.ulticode.recommend.api.RecommendService;
import com.ulticode.recommend.api.dto.RecommendRequest;
import com.ulticode.recommend.api.dto.RecommendResponse;
import com.ulticode.recommend.api.dto.RecommendResult;
import com.ulticode.recommend.api.enums.RecommendScenario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Implementation of RecommendationService using Dubbo RPC.
 * Directly calls the recommendation provider service via Dubbo,
 * eliminating the HTTP → Dubbo protocol translation layer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    @DubboReference(check = false, timeout = 5000, retries = 1)
    private RecommendService recommendService;

    @Value("${recommendation.enabled:false}")
    private boolean enabled;

    private final ProblemMapper problemMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final ProblemTagMapper problemTagMapper;

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
            // Only check Dubbo connectivity, not business logic
            // Use a size of 0 to avoid triggering actual recommendation computation
            RecommendRequest request = RecommendRequest.builder()
                    .userId("health-check")
                    .scenario(RecommendScenario.DAILY)
                    .size(0)
                    .build();
            recommendService.recommend(request);

            RecommendResponseVO response = new RecommendResponseVO();
            response.setSuccess(true);
            response.setCode(200);
            response.setMessage("Recommendation service is healthy");
            return response;
        } catch (RpcException e) {
            log.warn("Recommendation service health check failed: {}", e.getMessage());
            return RecommendResponseVO.error(50000, "Recommendation service unavailable");
        }
    }

    /**
     * Calls the Dubbo recommendation service and converts the result.
     * Falls back to MySQL-based popular problems when Dubbo is unavailable.
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
                return fallbackToPopularProblems(limit, scenario);
            }
        // broad catch: Dubbo RPC failure falls back to MySQL popular problems
        } catch (Exception e) {
            log.warn("Dubbo recommendation service unavailable, falling back to popular problems: {}", e.getMessage());
            return fallbackToPopularProblems(limit, scenario);
        }
    }

    /**
     * Fallback: queries MySQL for popular problems sorted by acceptance rate.
     * Used when the Dubbo recommendation service is unavailable.
     */
    private RecommendResponseVO fallbackToPopularProblems(Integer limit, RecommendScenario scenario) {
        int size = limit != null ? limit : 10;
        String difficulty = switch (scenario) {
            case CHALLENGE -> "Hard";
            case WEAK_POINT -> "Medium";
            default -> null;
        };

        QueryWrapper<Problem> wrapper = new QueryWrapper<Problem>()
                .eq("is_deleted", false)
                .eq("is_published", true)
                .orderByDesc("acceptance_rate");

        if (difficulty != null) {
            wrapper.eq("difficulty", difficulty);
        }

        Page<Problem> page = new Page<>(1, size);
        List<Problem> problems = problemMapper.selectPage(page, wrapper).getRecords();
        List<RecommendItem> items = problems.stream()
                .map(this::convertProblemToItem)
                .collect(Collectors.toList());

        return RecommendResponseVO.success(items);
    }

    /**
     * Converts a Problem entity to a RecommendItem with tags.
     */
    private RecommendItem convertProblemToItem(Problem problem) {
        RecommendItem item = new RecommendItem();
        item.setProblemId(problem.getId());
        item.setTitle(problem.getTitle());
        item.setSlug(problem.getSlug());
        item.setDifficulty(problem.getDifficulty());
        item.setScore(problem.getAcceptanceRate() != null ? problem.getAcceptanceRate().floatValue() : 50f);
        item.setReason("热门推荐");

        List<String> tagIds = problemTagRelationMapper.findTagIdsByProblemId(problem.getId());
        if (!tagIds.isEmpty()) {
            List<ProblemTag> tags = problemTagMapper.selectBatchIds(tagIds);
            item.setTags(tags.stream().map(ProblemTag::getLabel).collect(Collectors.toList()));
        } else {
            item.setTags(List.of());
        }
        return item;
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
