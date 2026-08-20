package com.ulticode.app.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Entity-free App-owned aggregates used by the Admin Dashboard read seam.
 */
public record DashboardAppStatsDTO(
        long totalProblems,
        long publishedProblems,
        List<Count> problemsByDifficulty,
        List<Count> problemsByStatus,
        long totalContests,
        long upcomingContests,
        long runningContests,
        long finishedContests,
        long totalSolutions,
        long publishedSolutions,
        long flaggedSolutions,
        long forumPosts,
        long forumComments,
        long forumCommunities,
        long flaggedForumPosts,
        long flaggedForumComments) implements Serializable {

    private static final long serialVersionUID = 1L;

    public DashboardAppStatsDTO {
        problemsByDifficulty = problemsByDifficulty == null
                ? List.of() : List.copyOf(problemsByDifficulty);
        problemsByStatus = problemsByStatus == null
                ? List.of() : List.copyOf(problemsByStatus);
    }

    /** A stable key/count row for grouped owner-side aggregates. */
    public record Count(String key, long count) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
