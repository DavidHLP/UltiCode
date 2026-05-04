package com.ulticode.modules.problem.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * VersionWithDiff view object for version comparison result.
 * Contains two versions and their differences.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VersionWithDiffVO {

    private ProblemVersionVO fromVersion;

    private ProblemVersionVO toVersion;

    private List<VersionDiffVO> diffs;
}