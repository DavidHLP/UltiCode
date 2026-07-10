package com.ulticode.modules.contest.subscription;

import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default {@link ContestSubscriptionPolicy} implementation. Owns the
 * {@link ContestParticipantMapper} lookup and the eligibility rules.
 *
 * <p>The transport adapter is responsible for parsing the topic
 * ({@code /topic/contest/{id}}) and extracting the user from the
 * session — the policy only sees a clean
 * {@link ContestSubscribeRequest}.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class DefaultContestSubscriptionPolicy implements ContestSubscriptionPolicy {

    private final ContestParticipantMapper contestParticipantMapper;

    @Override
    public SubscriptionDecision evaluate(ContestSubscribeRequest request) {
        if (request.contestId().isEmpty()) {
            return SubscriptionDecision.allow();
        }
        if (request.userId() == null) {
            return SubscriptionDecision.deny(
                    ContestSubscriptionPolicy.Verdict.DENY_NOT_AUTHENTICATED,
                    "Not authenticated");
        }
        Optional<ContestParticipant> participant = contestParticipantMapper
                .findByContestIdAndUserId(request.contestId().get(), request.userId());
        if (participant.isEmpty()) {
            return SubscriptionDecision.deny(
                    ContestSubscriptionPolicy.Verdict.DENY_NOT_REGISTERED,
                    "Not registered for contest " + request.contestId().get());
        }
        return SubscriptionDecision.allow();
    }
}
