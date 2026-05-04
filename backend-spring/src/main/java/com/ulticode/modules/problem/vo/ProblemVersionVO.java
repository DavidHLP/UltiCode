package com.ulticode.modules.problem.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * ProblemVersion view object for version history list API.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemVersionVO {

    private String id;

    private Integer versionNumber;

    private String changeSummary;

    private String changeType;

    private String createdAt;

    private String createdBy;
}