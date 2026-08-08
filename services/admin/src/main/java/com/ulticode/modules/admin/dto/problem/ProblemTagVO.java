package com.ulticode.modules.admin.dto.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Problem tag VO for API responses.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemTagVO {

    private String id;

    private String label;
}
