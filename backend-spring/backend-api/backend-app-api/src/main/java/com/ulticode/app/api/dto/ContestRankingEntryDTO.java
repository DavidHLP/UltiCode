package com.ulticode.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Entity-free live contest ranking entry, consumed by websocket and admin
 * modules via {@link com.ulticode.app.api.service.ContestLiveRankingReadPort}.
 *
 * <p>Promoted from {@code com.ulticode.modules.contest.dto.LiveRankingEntryVO}
 * during P7-RELOCATE-CONTEST-001 so cross-module consumers depend on the
 * contract module, not the contest implementation.
 *
 * @author ulticode
 */
@Schema(description = "Live contest ranking entry")
public class ContestRankingEntryDTO {

    @Schema(description = "User rank in the contest")
    private Integer rank;

    @Schema(description = "User ID")
    private String userId;

    @Schema(description = "Username")
    private String username;

    @Schema(description = "User display name")
    private String name;

    @Schema(description = "User avatar URL")
    private String avatar;

    @Schema(description = "User score")
    private Long score;

    @Schema(description = "Penalty time")
    private Long penalty;

    @Schema(description = "Number of problems solved")
    private Integer problemsSolved;

    @Schema(description = "Whether this is the current user")
    private Boolean isCurrentUser;

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Long getScore() { return score; }
    public void setScore(Long score) { this.score = score; }
    public Long getPenalty() { return penalty; }
    public void setPenalty(Long penalty) { this.penalty = penalty; }
    public Integer getProblemsSolved() { return problemsSolved; }
    public void setProblemsSolved(Integer problemsSolved) { this.problemsSolved = problemsSolved; }
    public Boolean getIsCurrentUser() { return isCurrentUser; }
    public void setIsCurrentUser(Boolean isCurrentUser) { this.isCurrentUser = isCurrentUser; }
}
