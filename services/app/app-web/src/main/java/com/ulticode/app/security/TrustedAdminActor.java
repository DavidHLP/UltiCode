package com.ulticode.app.security;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared fail-closed checks for delegated admin actors on App-owned write
 * providers.
 *
 * <p>Providers previously each kept a private copy of this shape; the shared
 * helper keeps the security gate identical everywhere: a syntactically admin
 * actor, an authorizer answer, and fail-closed handling when the authorizer
 * itself throws.
 */
public final class TrustedAdminActor {

    private static final Logger log = LoggerFactory.getLogger(TrustedAdminActor.class);

    private TrustedAdminActor() {
    }

    /** Whether the actor structurally claims an admin identity. */
    public static boolean isAdminRole(ActorDelegation actor) {
        return actor != null
                && actor.actorId() != null && !actor.actorId().isBlank()
                && ("ADMIN".equalsIgnoreCase(actor.actorType())
                        || "SUPER_ADMIN".equalsIgnoreCase(actor.actorType()));
    }

    /**
     * Fail-closed authorization: {@code false} when the authorizer rejects
     * the actor or itself fails.
     */
    public static boolean isTrusted(
            AdminActorAuthorizer authorizer, ActorDelegation actor, String context) {
        try {
            return authorizer.isAuthorized(actor);
        } catch (RuntimeException exception) {
            log.warn("{}: actor authorization failed", context, exception);
            return false;
        }
    }

    /**
     * Throwing variant for providers whose write path surfaces
     * {@link BusinessException}.
     */
    public static void requireTrusted(
            AdminActorAuthorizer authorizer, ActorDelegation actor, String context) {
        if (!isAdminRole(actor)) {
            throw new BusinessException(BaseErrorCode.FORBIDDEN, context + " requires an admin actor");
        }
        if (!isTrusted(authorizer, actor, context)) {
            throw new BusinessException(BaseErrorCode.FORBIDDEN, context + " requires a trusted admin actor");
        }
    }
}
