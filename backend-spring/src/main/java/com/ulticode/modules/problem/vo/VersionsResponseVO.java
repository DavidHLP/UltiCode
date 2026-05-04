package com.ulticode.modules.problem.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Paginated response wrapper for problem version history list.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VersionsResponseVO {

    private List<ProblemVersionVO> items;

    private Long total;

    private Integer page;

    private Integer pageSize;

    private Integer totalPages;
}
