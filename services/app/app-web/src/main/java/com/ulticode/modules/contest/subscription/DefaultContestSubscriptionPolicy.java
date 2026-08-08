package com.ulticode.modules.contest.subscription;

import com.ulticode.app.api.service.ContestSubscriptionPolicy;
import com.ulticode.app.api.service.ContestSubscriptionPolicy.ContestSubscribeRequest;
import com.ulticode.app.api.service.ContestSubscriptionPolicy.SubscriptionDecision;
import com.ulticode.app.api.service.ContestSubscriptionPolicy.Verdict;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default {@link ContestSubscriptionPolicy} implementation. Owns the
 * {@link ContestParticipantMapper} lookup and the eligibility rules.
 *
 * <p>P7-RELOCATE-CONTEST-001: now implements the app-api
 * {@link ContestSubscriptionPolicy} interface directly.
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
                    Verdict.DENY_NOT_AUTHENTICATED,
                    "Not authenticated");
        }
        Optional<ContestParticipant> participant = contestParticipantMapper
                .findByContestIdAndUserId(request.contestId().get(), request.userId());
        if (participant.isEmpty()) {
            return SubscriptionDecision.deny(
                    Verdict.DENY_NOT_REGISTERED,
                    "Not registered for contest " + request.contestId().get());
        }
        return SubscriptionDecision.allow();
    }
}
