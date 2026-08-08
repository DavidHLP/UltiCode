package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.QueueHealthSnapshotDTO;

/**
 * Narrow read-port for monitoring to probe queue health.
 * Extracted from queue.inspector.QueueInspector for P7-INFRA-S2.
 */
public interface QueueHealthProbePort {
    QueueHealthSnapshotDTO getQueueHealthSnapshot(String queueName);
}
