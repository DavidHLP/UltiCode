package com.ulticode.modules.contest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Contest announcement entity - announcements within a contest.
 * Note: No updatedAt field (V3 table has no updated_at column).
 */
@Data
@TableName("contest_announcements")
public class ContestAnnouncement {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String contestId;

    private String title;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Boolean isPinned;
}
