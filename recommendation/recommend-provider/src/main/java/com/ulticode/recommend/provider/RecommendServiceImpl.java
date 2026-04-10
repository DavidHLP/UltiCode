package com.ulticode.recommend.provider;

import com.ulticode.recommend.api.RecommendService;
import com.ulticode.recommend.api.dto.RecommendItem;
import com.ulticode.recommend.api.dto.RecommendRequest;
import com.ulticode.recommend.api.dto.RecommendResult;
import com.ulticode.recommend.api.dto.RecommendResponse;
import com.ulticode.recommend.api.enums.RecommendScenario;
import com.ulticode.recommend.core.RecommendEngine;
import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.UserProfile;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dubbo3 service implementation for the recommendation system.
 *
 * <p>This service:
 * <ul>
 *   <li>Accepts {@link RecommendRequest} from API layer</li>
 *   <li>Converts to core models ({@link RecommendContext}, {@link UserProfile})</li>
 *   <li>Delegates to {@link RecommendEngine} for recommendation generation</li>
 *   <li>Converts results back to API DTOs</li>
 * </ul>
 */
@DubboService
public class RecommendServiceImpl implements RecommendService {

    private static final Logger log = LoggerFactory.getLogger(RecommendServiceImpl.class);

    private final RecommendEngine recommendEngine;

    /**
     * Creates a new RecommendServiceImpl with Spring-injected recommend engine.
     *
     * @param recommendEngine the recommendation engine (configured by RecommendationEngineConfig)
     */
    public RecommendServiceImpl(RecommendEngine recommendEngine) {
        this.recommendEngine = recommendEngine;
    }

    /**
     * Cache key: userId + scenario + size.
     * TTL: 5 minutes (configured in CacheConfig).
     * Condition: Only cache valid requests (non-blank userId).
     */
    @Override
    @Cacheable(
            value = "recommendations",
            key = "#request.userId + '_' + #request.scenario + '_' + #request.size",
            condition = "#request != null && #request.userId != null && !#request.userId.isBlank()"
    )
    public RecommendResponse<RecommendResult> recommend(RecommendRequest request) {
        log.info("Received recommendation request for user: {}", request.getUserId());

        try {
            // Validate request
            if (request.getUserId() == null || request.getUserId().isBlank()) {
                return RecommendResponse.fail(400, "User ID is required");
            }

            // Convert API request to core context
            RecommendContext context = convertToContext(request);

            // Build user profile (in production, this would be fetched from a data store)
            UserProfile profile = buildUserProfile(request);

            // Execute recommendation pipeline
            List<com.ulticode.recommend.core.model.RecommendItem> coreResults =
                    recommendEngine.recommend(context, profile);

            // Convert core results to API DTOs
            List<RecommendItem> apiItems = convertToApiItems(coreResults);

            // Build result
            RecommendResult result = RecommendResult.builder()
                    .items(apiItems)
                    .totalCount(apiItems.size())
                    .scenario(request.getScenario())
                    .generatedAt(LocalDateTime.now())
                    .build();

            log.info("Generated {} recommendations for user: {}", apiItems.size(), request.getUserId());

            return RecommendResponse.success(result);

        } catch (Exception e) {
            log.error("Error generating recommendations for user: {}", request.getUserId(), e);
            return RecommendResponse.fail(500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Converts API request to core context.
     *
     * @param request the API request
     * @return the core context
     */
    private RecommendContext convertToContext(RecommendRequest request) {
        RecommendContext.Scenario scenario = convertScenario(request.getScenario());

        String[] targetTags = null;
        if (request.getTargetTags() != null && !request.getTargetTags().isEmpty()) {
            targetTags = request.getTargetTags().toArray(new String[0]);
        }

        return RecommendContext.builder()
                .userId(request.getUserId())
                .size(request.getSize())
                .scenario(scenario)
                .sourceProblemId(request.getSourceProblemId())
                .targetTags(targetTags)
                .includeSolved(request.isIncludeSolved())
                .build();
    }

    /**
     * Converts API scenario enum to core scenario enum.
     *
     * @param apiScenario the API scenario
     * @return the core scenario
     */
    private RecommendContext.Scenario convertScenario(RecommendScenario apiScenario) {
        if (apiScenario == null) {
            return RecommendContext.Scenario.DAILY;
        }

        return switch (apiScenario) {
            case DAILY -> RecommendContext.Scenario.DAILY;
            case SIMILAR -> RecommendContext.Scenario.SIMILAR;
            case WEAK_POINT -> RecommendContext.Scenario.WEAK_POINT;
            case CHALLENGE -> RecommendContext.Scenario.CHALLENGE;
        };
    }

    /**
     * Builds a user profile for the recommendation engine.
     *
     * <p>In production, this would fetch user data from a database or cache.
     * For now, returns a minimal profile.
     *
     * @param request the recommendation request
     * @return the user profile
     */
    private UserProfile buildUserProfile(RecommendRequest request) {
        return UserProfile.builder()
                .userId(request.getUserId())
                .solvedProblems(Set.of())  // In production, fetch from data store
                .rating(1500)  // Default rating
                .build();
    }

    /**
     * Converts core recommendation items to API DTOs.
     *
     * @param coreItems the core items
     * @return the API items
     */
    private List<RecommendItem> convertToApiItems(
            List<com.ulticode.recommend.core.model.RecommendItem> coreItems) {

        if (coreItems == null || coreItems.isEmpty()) {
            return new ArrayList<>();
        }

        return coreItems.stream()
                .map(this::convertToApiItem)
                .collect(Collectors.toList());
    }

    /**
     * Converts a single core recommendation item to an API DTO.
     *
     * @param coreItem the core item
     * @return the API item
     */
    private RecommendItem convertToApiItem(com.ulticode.recommend.core.model.RecommendItem coreItem) {
        List<String> tags = null;
        if (coreItem.getTags() != null) {
            tags = new ArrayList<>(coreItem.getTags());
        }

        return RecommendItem.builder()
                .problemId(coreItem.getProblemId())
                .slug(coreItem.getSlug())
                .title(coreItem.getTitle())
                .difficulty(coreItem.getDifficulty())
                .score(coreItem.getScore())
                .tags(tags)
                .reason(coreItem.getReason())
                .build();
    }
}
