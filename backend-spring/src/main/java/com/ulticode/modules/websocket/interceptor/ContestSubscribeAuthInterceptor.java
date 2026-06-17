package com.ulticode.modules.websocket.interceptor;

import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
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
 * <p>The handshake + JWT channel interceptor (F-17 partial) verifies the user
 * is authenticated, but every authenticated user can still attempt to
 * SUBSCRIBE to {@code /topic/contest/{id}}. This interceptor parses the
 * destination and rejects subscriptions where the user has no
 * {@code contest_participants} row (REGISTERED / STARTED / FINISHED) for
 * the requested contest. Virtual sessions are allowed if the user has any
 * virtual participant row (is_virtual=1) for the contest.
 *
 * <p>Failure mode: throws {@link WebSocketAuthenticationException}, which
 * Spring's STOMP adapter translates into a STOMP ERROR frame. The client
 * receives the error and disconnects.
 */
@Component
public class ContestSubscribeAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ContestSubscribeAuthInterceptor.class);

    /** Matches {@code /topic/contest/{id}} or
     *  {@code /topic/contest/{id}/{sub-topic}} — anything starting with
     *  the contest prefix counts as a contest subscription. */
    private static final Pattern CONTEST_TOPIC =
            Pattern.compile("^/topic/contest/([^/]+)(?:/.*)?$");

    private final ContestParticipantMapper contestParticipantMapper;

    public ContestSubscribeAuthInterceptor(ContestParticipantMapper contestParticipantMapper) {
        this.contestParticipantMapper = contestParticipantMapper;
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
                    ErrorCode.WEBSOCKET_UNAUTHORIZED, "No session attributes");
        }
        Object userObj = attrs.get("user");
        if (!(userObj instanceof SocketClientData user)) {
            log.warn("R6.4 / F-17: SUBSCRIBE {} with no user, sessionId: {}",
                    destination, accessor.getSessionId());
            throw new WebSocketAuthenticationException(
                    ErrorCode.WEBSOCKET_UNAUTHORIZED, "Not authenticated");
        }

        // Allow any participant row (real or virtual) for the contest.
        // Public broadcast topics still go through (no contestId match).
        Optional<ContestParticipant> participant = contestParticipantMapper
                .findByContestIdAndUserId(contestId, user.userId());
        if (participant.isEmpty()) {
            log.warn("R6.4 / F-17: user {} denied SUBSCRIBE to {} (not registered)",
                    user.userId(), destination);
            throw new WebSocketAuthenticationException(
                    ErrorCode.FORBIDDEN,
                    "Not registered for contest " + contestId);
        }

        log.debug("R6.4 / F-17: user {} allowed SUBSCRIBE to {} (isVirtual={})",
                user.userId(), destination, participant.get().getIsVirtual());
        return message;
    }
}
