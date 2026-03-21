package com.ulticode.modules.problemlist.dto;

import lombok.Data;

import java.util.List;

/**
 * View object for user's problem lists overview.
 */
@Data
public class UserProblemListsVO {
    private List<ProblemListSummaryVO> ownLists;
    private List<ProblemListSummaryVO> savedLists;
    private List<ProblemListSummaryVO> featuredLists;
    private List<CategorySummaryVO> categories;
}
