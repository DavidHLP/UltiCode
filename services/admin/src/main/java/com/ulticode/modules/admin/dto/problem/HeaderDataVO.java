package com.ulticode.modules.admin.dto.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Header data VO for problem header tab.
 * Contains basic problem information.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HeaderDataVO {

    private String id;

    private String title;

    private String slug;

    /**
     * Difficulty: EASY, MEDIUM, HARD
     */
    private String difficulty;

    /**
     * Status: solved, attempted, todo
     */
    private String status;

    private Boolean isPremium;

    private Boolean isPublished;

    private LocalDateTime publishedAt;
}
