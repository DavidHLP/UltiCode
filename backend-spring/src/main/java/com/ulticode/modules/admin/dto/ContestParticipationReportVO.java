package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Contest Participation Report View Object.
 * Contains contest participation statistics and trends.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContestParticipationReportVO {

    /**
     * Participation trend data point.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipationTrend {
        private String date;
        private Integer contests;
        private Integer participants;
    }

    /**
     * Stats by contest type.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeStats {
        private String type;
        private Integer count;
        private Double avgParticipants;
    }

    /**
     * Top contest data point.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopContest {
        private String contestId;
        private String title;
        private Integer participants;
        private Double completionRate;
    }

    /**
     * Virtual participation stats.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VirtualParticipation {
        private Integer total;
        private Double averageCompletionRate;
    }

    /**
     * Total number of contests.
     */
    private Integer totalContests;

    /**
     * Total unique participants.
     */
    private Long totalParticipants;

    /**
     * Average participants per contest.
     */
    private Double averageParticipantsPerContest;

    /**
     * Participation trend over time.
     */
    private List<ParticipationTrend> participationTrend;

    /**
     * Stats grouped by contest type.
     */
    private List<TypeStats> byType;

    /**
     * Top contests by participation.
     */
    private List<TopContest> topContests;

    /**
     * Virtual participation statistics.
     */
    private VirtualParticipation virtualParticipation;
}
