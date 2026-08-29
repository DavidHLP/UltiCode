package com.ulticode.auth.security;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.WriteCommand;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shared trust gate for Auth-owned Dubbo write providers.
 *
 * <p>Combines the two checks every provider applies before a durable
 * mutation: the BOOTSTRAP actor is restricted to an explicit per-provider
 * operation allowlist (scoped to the canonical {@code bootstrap} identity),
 * and every non-self-service actor must carry a verified delegation
 * assertion. Verification failures (including runtime errors from the
 * verifier) fail closed.
 */
@Component
public class ProviderActorTrustGate {

    private static final Logger log = LoggerFactory.getLogger(ProviderActorTrustGate.class);
    private static final String BOOTSTRAP_ACTOR_TYPE = "BOOTSTRAP";
    private static final String BOOTSTRAP_ACTOR_ID = "bootstrap";

    private final InternalDelegationAssertionVerifier delegationVerifier;

    public ProviderActorTrustGate(InternalDelegationAssertionVerifier delegationVerifier) {
        this.delegationVerifier = delegationVerifier;
    }

    /**
     * Whether the command's actor may execute {@code operation}: the BOOTSTRAP
     * actor only within {@code bootstrapOperations}, any other actor only
     * with a verified delegation assertion.
     */
    public boolean isTrustedForOperation(WriteCommand command, String operation,
            Set<String> bootstrapOperations) {
        if (command == null) {
            return false;
        }
        if (!isAllowedBootstrapOperation(command, operation, bootstrapOperations)) {
            return false;
        }
        return isTrusted(command.actor());
    }

    /** Fail-closed assertion verification for the given actor. */
    public boolean isTrusted(ActorDelegation actor) {
        try {
            return delegationVerifier.isTrusted(actor);
        } catch (RuntimeException exception) {
            log.warn("Account actor authorization failed", exception);
            return false;
        }
    }

    private static boolean isAllowedBootstrapOperation(
            WriteCommand command, String operation, Set<String> bootstrapOperations) {
        ActorDelegation actor = command.actor();
        if (actor == null || !BOOTSTRAP_ACTOR_TYPE.equalsIgnoreCase(actor.actorType())) {
            return true;
        }
        return BOOTSTRAP_ACTOR_ID.equals(actor.actorId())
                && BOOTSTRAP_ACTOR_ID.equals(actor.delegatorId())
                && bootstrapOperations.contains(operation);
    }
}
