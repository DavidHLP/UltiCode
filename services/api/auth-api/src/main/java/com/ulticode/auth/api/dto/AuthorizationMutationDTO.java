package com.ulticode.auth.api.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;

/** Post-mutation projection for one Auth-owned direct permission delta.
 * {@code expiresAt} uses an explicit offset for cross-JVM determinism. */
public record AuthorizationMutationDTO(
        String accountId,
        String operation,
        String action,
        String resource,
        String source,
        OffsetDateTime expiresAt,
        long version,
        boolean changed) implements Serializable {

    private static final long serialVersionUID = 1L;
}
