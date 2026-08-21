package com.ulticode.app.user.port;

import java.util.Collection;
import java.util.Map;

/**
 * Owner-composed User Facts View Projection shared by App, Search and
 * Moderation. The implementation owns account/profile batching, missing
 * account handling, freshness fields and unavailable-owner policy.
 */
public interface UserFactsProjection {

    UserFactView findById(String id);

    Map<String, UserFactView> findByIds(Collection<String> ids);

    /** Compose already-loaded account facts with one owner profile batch. */
    Map<String, UserFactView> compose(Collection<UserAccountFact> accounts);
}
