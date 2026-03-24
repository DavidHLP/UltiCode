package com.ulticode.modules.admin.dto.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Problem detail VO for nested detail data.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetailVO {

    private String id;

    private String summary;

    private String content;

    private Integer difficultyRating;

    private Integer likes;

    private Integer dislikes;

    private List<String> constraintsJson;

    private List<String> hints;
}
