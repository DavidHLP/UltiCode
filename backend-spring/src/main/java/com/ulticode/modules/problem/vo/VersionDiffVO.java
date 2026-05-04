package com.ulticode.modules.problem.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * VersionDiff view object representing a single field difference between versions.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VersionDiffVO {

    private String field;

    private Object oldValue;

    private Object newValue;
}