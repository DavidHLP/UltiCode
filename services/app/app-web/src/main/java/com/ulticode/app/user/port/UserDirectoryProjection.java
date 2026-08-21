package com.ulticode.app.user.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Narrow directory read seam for UserSummaryView callers.
 *
 * <p>Account state, profile display fields and directory pagination stay behind
 * this interface; Search and Moderation use UserFactsProjection instead.</p>
 */
public interface UserDirectoryProjection {

    UserSummaryView selectById(String id);

    UserSummaryView selectByUsername(String username);

    UserSummaryView selectByEmail(String email);

    Map<String, UserSummaryView> selectByIds(Collection<String> ids);

    List<UserSummaryView> selectActiveUsers(int limit, int offset);

    long countActiveUsers();

    int countById(String id);
}
