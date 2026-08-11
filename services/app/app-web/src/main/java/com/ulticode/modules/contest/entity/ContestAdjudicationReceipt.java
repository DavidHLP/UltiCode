package com.ulticode.modules.contest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Durable fence for one contest scoring application.
 *
 * <p>The unique {@code (submission_id, generation)} key makes a committed
 * receipt the idempotency decision for the whole adjudication transaction.</p>
 */
@Data
@TableName("contest_adjudication_receipts")
public class ContestAdjudicationReceipt {

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("submission_id")
    private String submissionId;

    private Long generation;

    private String verdict;

    @TableField("is_accepted")
    private Boolean accepted;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
