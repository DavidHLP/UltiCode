package com.ulticode.modules.search.port;

import lombok.Getter;
import lombok.Setter;

/**
 * Lightweight read row for the user search index (Q-read of the
 * Auth-owned {@code users} table). Holds only display/identity
 * columns — never credentials.
 */
@Getter
@Setter
public class UserSearchRow {

    private String id;
    private String username;
    private String name;
    private String avatar;
}
