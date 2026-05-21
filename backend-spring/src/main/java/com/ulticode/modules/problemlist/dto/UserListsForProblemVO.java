package com.ulticode.modules.problemlist.dto;

import lombok.Data;

import java.util.List;

/**
 * View object for user's lists for a specific problem.
 */
@Data
public class UserListsForProblemVO {
    private Long problemId;
    private List<ListStatusVO> lists;

    /**
     * View object for list with problem status.
     */
    @Data
    public static class ListStatusVO {
        private String id;
        private String name;
        private Boolean hasProblem;
        private Integer problemCount;
        private Boolean canEdit;
    }
}
