package com.ulticode.app.security;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * App-side admin actor authorization backed by the auth-owned identity
 * projection.
 *
 * <p>Privileged writes fail closed when the auth RPC is unavailable or returns
 * an incomplete result. This prevents an unverified actor from reaching the
 * durable command receipt boundary.
 */
@Slf4j
@Component
public class DubboIdentityActorAuthorizer implements AdminActorAuthorizer {

    @DubboReference(group = "backend-auth", check = false)
    private IdentityQueryService identityQueryService;

    private final InternalDelegationAssertionVerifier delegationVerifier;

    /** Spring injects the verified transport-identity seam. */
    @Autowired
    public DubboIdentityActorAuthorizer(InternalDelegationAssertionVerifier delegationVerifier) {
        this.delegationVerifier = delegationVerifier;
    }

    /** Package-private constructor for focused unit tests. */
    DubboIdentityActorAuthorizer(
            IdentityQueryService identityQueryService,
            InternalDelegationAssertionVerifier delegationVerifier) {
        this.identityQueryService = identityQueryService;
        this.delegationVerifier = delegationVerifier;
    }

    @Override
    public boolean isAuthorized(ActorDelegation actor) {
        if (actor == null || actor.actorId() == null || actor.actorId().isBlank()
                || actor.actorType() == null || actor.actorType().isBlank()) {
            return false;
        }
        if (!delegationVerifier.isTrusted(actor)) {
            log.warn("Delegation assertion missing or invalid for admin actor {}", actor.actorId());
            return false;
        }
        if (identityQueryService == null) {
            log.warn("IdentityQueryService unavailable; rejecting admin actor {}", actor.actorId());
            return false;
        }
        try {
            RpcResult<UserIdentityDTO> result = identityQueryService.getIdentity(actor.actorId());
            if (result == null || !result.success() || result.data() == null) {
                return false;
            }
            UserIdentityDTO identity = result.data();
            return identity.accountId() != null
                    && identity.accountId().equals(actor.actorId())
                    && identity.active()
                    && !identity.banned()
                    && identity.role() != null
                    && identity.role().equalsIgnoreCase(actor.actorType())
                    && ("ADMIN".equalsIgnoreCase(identity.role())
                    || "SUPER_ADMIN".equalsIgnoreCase(identity.role()));
        } catch (Exception exception) {
            log.warn("Admin actor authorization RPC failed for {}: {}",
                    actor.actorId(), exception.getMessage());
            return false;
        }
    }
}
