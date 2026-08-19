package com.ulticode.app.user.port;


import java.util.List;

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

    List<UserSummaryView> selectActiveUsers(int limit, int offset);

    long countActiveUsers();

    int countById(String id);
}
