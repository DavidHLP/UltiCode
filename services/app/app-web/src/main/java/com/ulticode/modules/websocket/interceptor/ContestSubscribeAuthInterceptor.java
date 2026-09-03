package com.ulticode.modules.websocket.interceptor;

import com.ulticode.app.error.WebSocketErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.modules.contest.subscription.ContestSubscriptionPolicy;
import com.ulticode.modules.contest.subscription.ContestSubscriptionPolicy.ContestSubscribeRequest;
import com.ulticode.modules.contest.subscription.ContestSubscriptionPolicy.SubscriptionDecision;
import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor.WebSocketAuthenticationException;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * R6.4 / F-17: SUBSCRIBE-frame authorization for contest topics.
 *
 * <p>Thin STOMP adapter. Parses the STOMP destination, extracts the
 * authenticated user from the session, and hands the request to
 * {@link ContestSubscriptionPolicy}. The policy owns the eligibility
 * rules; the interceptor only translates the policy verdict into a
 * STOMP ERROR frame.
 *
 * <p>Failure mode: throws {@link WebSocketAuthenticationException}, which
 * Spring's STOMP adapter translates into a STOMP ERROR frame. The client
 * receives the error and disconnects.
 *
 * <p>Architecture-review candidate #6: the previous implementation held
 * both the STOMP translation AND the {@code ContestParticipantMapper}
 * lookup. Moving the lookup into
 * {@link com.ulticode.modules.contest.subscription.DefaultContestSubscriptionPolicy}
 * means the contest subscription rules can be tested without a STOMP
 * broker and reused by any future transport (SSE, gRPC stream, etc.).
 */
@Component
public class ContestSubscribeAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ContestSubscribeAuthInterceptor.class);

    /** Matches {@code /topic/contest/{id}} or
     *  {@code /topic/contest/{id}/{sub-topic}} — anything starting with
     *  the contest prefix counts as a contest subscription. */
    private static final Pattern CONTEST_TOPIC =
            Pattern.compile("^/topic/contest/([^/]+)(?:/.*)?$");

    private final ContestSubscriptionPolicy subscriptionPolicy;

    public ContestSubscribeAuthInterceptor(ContestSubscriptionPolicy subscriptionPolicy) {
        this.subscriptionPolicy = subscriptionPolicy;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }
        Matcher m = CONTEST_TOPIC.matcher(destination);
        if (!m.matches()) {
            // Non-contest topics (e.g. /topic/broadcast, /user/queue/...) pass through.
            return message;
        }
        String contestId = m.group(1);

        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) {
            log.warn("R6.4 / F-17: SUBSCRIBE {} with no session attrs, sessionId: {}",
                    destination, accessor.getSessionId());
            throw new WebSocketAuthenticationException(
                    WebSocketErrorCode.UNAUTHORIZED, "No session attributes");
        }
        Object userObj = attrs.get("user");
        if (!(userObj instanceof SocketClientData user)) {
            log.warn("R6.4 / F-17: SUBSCRIBE {} with no user, sessionId: {}",
                    destination, accessor.getSessionId());
            throw new WebSocketAuthenticationException(
                    WebSocketErrorCode.UNAUTHORIZED, "Not authenticated");
        }

        // Translate the STOMP frame into a transport-agnostic request and
        // delegate the eligibility check to the policy module.
        SubscriptionDecision decision = subscriptionPolicy.evaluate(
                new ContestSubscribeRequest(
                        user.userId(),
                        destination,
                        Optional.of(contestId)));

        if (decision.verdict() != ContestSubscriptionPolicy.Verdict.ALLOW) {
            com.ulticode.common.error.NamespacedErrorCode code = decision.verdict() == ContestSubscriptionPolicy.Verdict.DENY_NOT_REGISTERED
                    ? com.ulticode.common.error.BaseErrorCode.FORBIDDEN
                    : WebSocketErrorCode.UNAUTHORIZED;
            log.warn("R6.4 / F-17: user {} denied SUBSCRIBE to {} ({})",
                    user.userId(), destination, decision.verdict());
            throw new WebSocketAuthenticationException(code, decision.reason());
        }

        log.debug("R6.4 / F-17: user {} allowed SUBSCRIBE to {}", user.userId(), destination);
        return message;
    }
}
