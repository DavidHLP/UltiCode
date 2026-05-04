package com.ulticode.modules.problem.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Paginated response wrapper for problem version history list.
 * Matches frontend VersionsResponse type: { versions, pagination: { total, page, limit, totalPages } }
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VersionsResponseVO {

    private List<ProblemVersionVO> versions;

    private Pagination pagination;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Pagination {
        private Long total;
        private Integer page;
        private Integer limit;
        private Integer totalPages;
    }
}
