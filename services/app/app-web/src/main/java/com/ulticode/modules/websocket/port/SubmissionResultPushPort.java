package com.ulticode.modules.websocket.port;

import com.ulticode.submission.api.dto.SubmissionResultPayload;

/**
 * Push port for delivering submission-result notifications.
 * Promoted from queue.port for P7-INFRA-S2.
 */
public interface SubmissionResultPushPort {
    void emitSubmissionResult(String userId, SubmissionResultPayload payload);
}
