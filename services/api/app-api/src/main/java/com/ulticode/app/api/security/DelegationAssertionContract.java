package com.ulticode.app.api.security;

/**
 * Wire-level names and claims for the signed Admin-to-App delegation assertion.
 *
 * <p>The assertion travels as a Dubbo attachment only as a transport carrier;
 * the App provider must verify its signature and claims before trusting it.
 */
public final class DelegationAssertionContract {

    public static final String ATTACHMENT_KEY = "ulticode-delegation-assertion";
    public static final String ISSUER = "backend-admin";
    public static final String AUDIENCE = "backend-app";
    public static final String ACTOR_SERVICE_CLAIM = "actor_service";
    public static final String ACTOR_TYPE_CLAIM = "actor_type";

    private DelegationAssertionContract() {
    }
}
