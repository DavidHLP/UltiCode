package com.ulticode.recommend.web;

import com.ulticode.recommend.api.RecommendService;
import com.ulticode.recommend.api.dto.RecommendRequest;
import com.ulticode.recommend.api.dto.RecommendResponse;
import com.ulticode.recommend.api.dto.RecommendResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for recommendation API endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    @DubboReference
    private RecommendService recommendService;

    /**
     * Get problem recommendations based on the request parameters.
     *
     * @param request the recommendation request
     * @return response containing recommendation results
     */
    @PostMapping
    public ResponseEntity<RecommendResponse<RecommendResult>> recommend(
            @Valid @RequestBody RecommendRequest request) {
        log.info("Received recommendation request for user: {}, scenario: {}",
                request.getUserId(), request.getScenario());

        try {
            RecommendResponse<RecommendResult> response = recommendService.recommend(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid recommendation request: {}", e.getMessage());
            RecommendResponse<RecommendResult> errorResponse = RecommendResponse.fail(
                    400, "Invalid request parameters");
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Error processing recommendation request", e);
            RecommendResponse<RecommendResult> errorResponse = RecommendResponse.fail(
                    500, "Internal server error");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Health check endpoint.
     *
     * @return health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(health);
    }
}
