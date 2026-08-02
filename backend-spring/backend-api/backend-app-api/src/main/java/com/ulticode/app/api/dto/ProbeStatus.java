package com.ulticode.app.api.dto;

/**
 * Outcome of a queue-depth probe.
 * Extracted from queue.dto for P7-INFRA-S2: monitoring (legacy) needs
 * this type but queue family relocated to backend-app.
 */
public enum ProbeStatus {
    OK,
    PROBE_FAILED
}
