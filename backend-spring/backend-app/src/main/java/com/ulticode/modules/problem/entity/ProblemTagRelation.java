package com.ulticode.modules.problem.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * Problem tag relation entity for many-to-many relationship.
 */
@Data
@TableName("problem_tag_relations")
public class ProblemTagRelation {

    @TableField("problem_id")
    private Long problemId;

    @TableField("tag_id")
    private String tagId;
}
