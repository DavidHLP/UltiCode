package com.ulticode.app.api.service;

import java.util.Optional;

/**
 * Contest subscription policy — owns participant eligibility for
 * contest topic subscriptions, with no knowledge of STOMP destination
 * or frame types.
 *
 * <p>Promoted from
 * {@code com.ulticode.modules.contest.subscription.ContestSubscriptionPolicy}
 * during P7-RELOCATE-CONTEST-001 so the websocket interceptor can depend
 * on the contract module.
 *
 * @author ulticode
 */
public interface ContestSubscriptionPolicy {

    /**
     * Evaluate a subscription request.
     */
    SubscriptionDecision evaluate(ContestSubscribeRequest request);

    record ContestSubscribeRequest(
            String userId,
            String topic,
            Optional<String> contestId) {
    }

    enum Verdict {
        ALLOW,
        DENY_NO_SESSION,
        DENY_NOT_AUTHENTICATED,
        DENY_NOT_REGISTERED
    }

    record SubscriptionDecision(Verdict verdict, String reason) {
        public static SubscriptionDecision allow() {
            return new SubscriptionDecision(Verdict.ALLOW, null);
        }

        public static SubscriptionDecision deny(Verdict verdict, String reason) {
            return new SubscriptionDecision(verdict, reason);
        }
    }
}
