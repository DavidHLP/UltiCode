package com.ulticode.auth.security.oauth.mapper;

import com.ulticode.auth.security.oauth.entity.OAuthProviderIdentity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Auth-owned OAuth provider identity persistence mapper.
 *
 * <p>AUTH-COMP-004: queries by (provider, providerUserId) to look up the
 * authoritative binding — not by email. Only active (unlinked_at IS NULL)
 * rows are considered for login; unlinked rows are historical records.
 */
@Mapper
public interface OAuthProviderIdentityMapper {

    String COLUMNS = "id, account_id, provider, provider_user_id, linked_at, unlinked_at";

    @Select("SELECT " + COLUMNS + " FROM oauth_provider_identities "
            + "WHERE provider = #{provider} AND provider_user_id = #{providerUserId} "
            + "AND unlinked_at IS NULL LIMIT 1")
    OAuthProviderIdentity findActiveByProviderAndProviderUserId(
            @Param("provider") String provider,
            @Param("providerUserId") String providerUserId);

    @Insert("INSERT INTO oauth_provider_identities (id, account_id, provider, provider_user_id) "
            + "VALUES (#{id}, #{accountId}, #{provider}, #{providerUserId})")
    int insert(OAuthProviderIdentity identity);
}
