package com.ulticode.modules.admin.dto.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Problem example VO for API responses.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemExampleVO {

    private String id;

    private String input;

    private String output;

    private String explanation;

    private Integer order;
}
