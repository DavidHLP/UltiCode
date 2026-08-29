package com.ulticode.modules.problem.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * ProblemVersionDetail view object for version detail API.
 * Contains full problem data at a specific version.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemVersionDetailVO extends ProblemVersionVO {

    private String title;

    private String slug;

    private String difficulty;

    private Boolean isPremium;

    private Boolean isPublished;

    private String summary;

    private String content;

    private List<String> constraints;

    private List<String> hints;

    private List<Map<String, Object>> examples;

    private List<Map<String, Object>> languages;

    private List<String> tags;
}