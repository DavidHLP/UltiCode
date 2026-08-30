package com.ulticode.websecurity.jwt;

import java.time.Duration;

/** Atomically claims a signed delegation assertion id for one target audience. */
@FunctionalInterface
public interface DelegationAssertionReplayGuard {

    boolean claim(String targetAudience, String jti, Duration ttl);
}
