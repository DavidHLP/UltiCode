package com.ulticode.modules.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Response VO for recommendation API.
 * Wraps the response from the external recommendation microservice.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecommendResponseVO {

    /**
     * Whether the request was successful
     */
    private Boolean success;

    /**
     * Response code (200 for success)
     */
    private Integer code;

    /**
     * Response message
     */
    private String message;

    /**
     * Response data containing recommendation items
     */
    private RecommendData data;

    /**
     * Inner class for recommendation data
     */
    @Data
    public static class RecommendData {
        /**
         * List of recommended items
         */
        private List<RecommendItem> items;
    }

    /**
     * Inner class representing a single recommendation item
     */
    @Data
    public static class RecommendItem {
        /**
         * Problem ID
         */
        private Long problemId;

        /**
         * Problem title
         */
        private String title;

        /**
         * Problem slug (URL-friendly identifier)
         */
        private String slug;

        /**
         * Problem difficulty: EASY, MEDIUM, HARD
         */
        private String difficulty;

        /**
         * Recommendation score (0.0 to 1.0)
         */
        private Float score;

        /**
         * Reason for this recommendation
         */
        private String reason;

        /**
         * Tags associated with the problem
         */
        private List<String> tags;
    }

    /**
     * Create a success response with items
     *
     * @param items the recommendation items
     * @return success response
     */
    public static RecommendResponseVO success(List<RecommendItem> items) {
        RecommendResponseVO response = new RecommendResponseVO();
        response.setSuccess(true);
        response.setCode(200);
        response.setMessage("success");

        RecommendData data = new RecommendData();
        data.setItems(items);
        response.setData(data);

        return response;
    }

    /**
     * Create an error response
     *
     * @param code    error code
     * @param message error message
     * @return error response
     */
    public static RecommendResponseVO error(Integer code, String message) {
        RecommendResponseVO response = new RecommendResponseVO();
        response.setSuccess(false);
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
