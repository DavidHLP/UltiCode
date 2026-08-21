package com.ulticode.app.user.port;

import java.util.Collection;
import java.util.Map;

/**
 * Owner-composed user facts seam shared by App, Search, and Moderation.
 * Account/profile lookup, missing-account handling, and unavailable-owner
 * policy stay behind this deep module; consumers receive only typed facts.
 */
public interface UserFactsReadPort {

    UserFactView findById(String id);

    Map<String, UserFactView> findByIds(Collection<String> ids);

    /** Compose already-loaded account facts with one owner profile batch. */
    Map<String, UserFactView> compose(Collection<UserAccountFact> accounts);
}
