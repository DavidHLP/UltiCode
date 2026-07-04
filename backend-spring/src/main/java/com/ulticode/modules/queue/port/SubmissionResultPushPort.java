package com.ulticode.modules.queue.port;

import com.ulticode.modules.websocket.contest.dto.SubmissionResultPayload;

/**
 * Push port the queue / judge module uses to deliver a real-time
 * {@link SubmissionResultPayload} to the judging user's STOMP queue after
 * a verdict is written.
 *
 * <p>Replaces the cross-module leak point
 * {@code JudgeWorkerProcessor.pushResult} had on
 * {@code com.ulticode.modules.websocket.service.RealtimeService}. The
 * judge worker only ever emits one event shape (the submission result)
 * to one destination (the judging user's STOMP queue) — perfect ISP
 * target for a single-method port.
 *
 * <p>Contract: best-effort. The {@code submissions} row carries the
 * durable verdict; the WebSocket push is the live notification.
 *
 * @author ulticode
 */
public interface SubmissionResultPushPort {

    /**
     * Push a submission-result envelope to the user's STOMP queue.
     *
     * <p>Implementations MUST NOT throw on a missing or disconnected session.
     *
     * @param userId  the judging user id (must not be {@code null})
     * @param payload the wire-format submission-result envelope (must not be {@code null})
     */
    void emitSubmissionResult(String userId, SubmissionResultPayload payload);
}