package com.ulticode.modules.admin.dto.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Problem language VO for API responses.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemLanguageVO {

    private String id;

    private String language;

    private String value;

    private String style;

    private String starterCode;
}
