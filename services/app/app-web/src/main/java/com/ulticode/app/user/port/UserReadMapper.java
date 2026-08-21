package com.ulticode.app.user.port;


import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Consumer-owned read port composing Auth account data with App profile data.
 *
 * <p>The implementation must query owner contracts and the App-owned
 * {@code user_profiles} table; it must not join a local {@code users} table.
 */
public interface UserReadMapper {

    UserSummaryView selectById(String id);

    UserSummaryView selectByUsername(String username);

    UserSummaryView selectByEmail(String email);

    /**
     * Compose existing account/profile facts for a bounded set of IDs.
     * Missing accounts are absent; owner-query failures fail closed.
     */
    Map<String, UserSummaryView> selectByIds(Collection<String> ids);

    List<UserSummaryView> selectActiveUsers(int limit, int offset);

    long countActiveUsers();

    int countById(String id);
}
