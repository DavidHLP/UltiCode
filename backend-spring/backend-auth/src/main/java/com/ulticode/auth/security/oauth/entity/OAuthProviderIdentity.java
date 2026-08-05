package com.ulticode.auth.security.oauth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Persistence projection for {@code oauth_provider_identities}.
 *
 * <p>AUTH-COMP-004: binds a provider's stable user id to an UltiCode account,
 * separately from email, to close the wrong-account-merge vector (R4).
 */
@Data
public class OAuthProviderIdentity {
    private String id;
    private String accountId;
    private String provider;
    private String providerUserId;
    private LocalDateTime linkedAt;
    private LocalDateTime unlinkedAt;
}
