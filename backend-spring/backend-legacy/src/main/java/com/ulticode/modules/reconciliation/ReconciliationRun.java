package com.ulticode.modules.reconciliation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entity for the {@code reconciliation_runs} table (P5-RECONCILE-001).
 *
 * <p>Persists each nightly reconciliation run so divergence history is queryable.
 */
@Data
@TableName("reconciliation_runs")
public class ReconciliationRun {

    @TableId(type = IdType.INPUT)
    @TableField("run_id")
    private String runId;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /** Auth/Admin/App/ALL */
    private String owner;

    /** RUNNING/COMPLETED/FAILED */
    private String status;

    @TableField("divergence_count")
    private Integer divergenceCount;

    @TableField("orphan_count")
    private Integer orphanCount;

    /** JSON summary of reconciliation results */
    private String detail;
}
