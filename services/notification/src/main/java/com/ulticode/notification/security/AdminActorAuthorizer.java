package com.ulticode.notification.security;

import com.ulticode.app.api.command.ActorDelegation;

/**
 * Verifies the admin identity carried across an App administration command.
 *
 * <p>This provider-side check runs before a command receipt is claimed so an
 * unverified actor cannot become durable audit identity for a privileged
 * mutation.
 */
public interface AdminActorAuthorizer {

    /**
     * Return whether the delegated actor is an active, unbanned admin whose
     * authoritative role matches the command's claimed actor type.
     */
    boolean isAuthorized(ActorDelegation actor);
}
