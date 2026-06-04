package com.ulticode.modules.contest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Global ranking entity
 */
@Data
@TableName("global_rankings")
public class GlobalRanking {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String username;

    private Integer globalRank;

    /**
     * Current rating (default 1500)
     */
    private Integer rating;

    /**
     * Maximum rating achieved (default 1500)
     */
    private Integer maxRating;

    /**
     * Number of contests attended
     */
    private Integer contestsAttended;

    private String avatar;

    private String country;

    private String badge;

    /**
     * Number of rated contests
     */
    private Integer contestsRated;

    private String lastContestId;

    /**
     * Maximum rating title
     */
    private String maxRatingTitle;

    /**
     * Current rating title
     */
    private String ratingTitle;

    @TableField(exist = false)
    private String name;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
