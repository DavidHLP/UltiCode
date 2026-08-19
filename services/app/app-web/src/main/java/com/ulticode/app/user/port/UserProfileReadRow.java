package com.ulticode.app.user.port;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Minimal App-owned profile projection used by owner-composed search reads. */
@Getter
@Setter
public class UserProfileReadRow {

    private String accountId;
    private String name;
    private String avatar;
    private LocalDateTime updatedAt;
}
