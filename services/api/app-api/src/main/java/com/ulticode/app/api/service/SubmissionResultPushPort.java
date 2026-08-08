package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.SubmissionResultPayload;

/**
 * Push port for delivering submission-result notifications.
 * Promoted from queue.port for P7-INFRA-S2.
 */
public interface SubmissionResultPushPort {
    void emitSubmissionResult(String userId, SubmissionResultPayload payload);
}
