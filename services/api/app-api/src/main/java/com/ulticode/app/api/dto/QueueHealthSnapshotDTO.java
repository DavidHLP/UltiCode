package com.ulticode.app.api.dto;

import java.io.Serializable;
import lombok.Builder;

/**
 * Health snapshot of one queue for monitoring.
 * Extracted from queue.dto for P7-INFRA-S2.
 */
@Builder
public class QueueHealthSnapshotDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String queueName;
    private long waitingDepth = 0L;
    private long failedCount = 0L;
    private long completedCount = 0L;
    private ProbeStatus probeStatus = ProbeStatus.OK;

    public QueueHealthSnapshotDTO() {}

    public QueueHealthSnapshotDTO(String queueName, long waitingDepth, long failedCount,
                                   long completedCount, ProbeStatus probeStatus) {
        this.queueName = queueName;
        this.waitingDepth = waitingDepth;
        this.failedCount = failedCount;
        this.completedCount = completedCount;
        this.probeStatus = probeStatus;
    }

    public String getQueueName() { return queueName; }
    public void setQueueName(String queueName) { this.queueName = queueName; }
    public long getWaitingDepth() { return waitingDepth; }
    public void setWaitingDepth(long v) { this.waitingDepth = v; }
    public long getFailedCount() { return failedCount; }
    public void setFailedCount(long v) { this.failedCount = v; }
    public long getCompletedCount() { return completedCount; }
    public void setCompletedCount(long v) { this.completedCount = v; }
    public ProbeStatus getProbeStatus() { return probeStatus; }
    public void setProbeStatus(ProbeStatus v) { this.probeStatus = v; }
}
