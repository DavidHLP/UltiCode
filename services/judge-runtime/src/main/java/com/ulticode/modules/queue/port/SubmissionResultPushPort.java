package com.ulticode.modules.queue.port;

/**
 * Queue-local SubmissionResultPushPort that inherits the contract from
 * the app-api port. Kept as a sub-interface so queue-internal code can
 * depend on the local type while the wire contract lives in app-api.
 *
 * <p>P7-INFRA-S2: the wire contract was promoted to
 * {@link com.ulticode.app.api.service.SubmissionResultPushPort} so
 * the websocket adapter in backend-legacy can implement it without
 * a compile-time dependency on backend-app.
 */
public interface SubmissionResultPushPort extends com.ulticode.app.api.service.SubmissionResultPushPort {
}
