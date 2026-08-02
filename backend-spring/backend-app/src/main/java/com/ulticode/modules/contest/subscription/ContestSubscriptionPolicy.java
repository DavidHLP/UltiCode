package com.ulticode.modules.contest.subscription;

import java.util.Optional;

/**
 * Contest subscription policy module — owns participant eligibility for
 * WebSocket /topic/contest/{id} subscriptions, with no knowledge of STOMP
 * destination or frame types.
 *
 * <p>Extracted from the previous {@code ContestSubscribeAuthInterceptor}
 * (architecture-review candidate #6) so the interceptor becomes a thin
 * STOMP adapter and any other transport (e.g. a future SSE endpoint) can
 * reuse the same policy without dragging in
 * {@code org.springframework.messaging.simp.stomp}.
 *
 * <p>The implementation lives in
 * {@link com.ulticode.modules.contest.subscription.DefaultContestSubscriptionPolicy}.
 *
 * @author ulticode
 */
public interface ContestSubscriptionPolicy {

    /**
     * Evaluate a subscription request. Returns a {@link SubscriptionDecision}
     * with the verdict and the optional reason / metadata the caller can
     * translate into the transport-specific error (STOMP ERROR frame,
     * HTTP 403, etc.).
     */
    SubscriptionDecision evaluate(ContestSubscribeRequest request);

    /**
     * Input to the policy. The transport-agnostic shape is intentional:
     * a STOMP interceptor, an SSE handler, or a future gRPC server-stream
     * all build the same request from their per-protocol data.
     */
    record ContestSubscribeRequest(
            String userId,
            String topic,
            /** Optional contest identifier parsed from the topic. The policy
             *  is allowed to reject when the topic is not a contest topic;
             *  the interceptor handles that case before calling the
             *  policy. */
            Optional<String> contestId) {
    }

    enum Verdict {
        ALLOW,
        DENY_NO_SESSION,
        DENY_NOT_AUTHENTICATED,
        DENY_NOT_REGISTERED
    }

    /**
     * Policy verdict + the data the transport adapter needs to render
     * the error.
     */
    record SubscriptionDecision(Verdict verdict, String reason) {
        public static SubscriptionDecision allow() {
            return new SubscriptionDecision(Verdict.ALLOW, null);
        }

        public static SubscriptionDecision deny(Verdict verdict, String reason) {
            return new SubscriptionDecision(verdict, reason);
        }
    }
}
