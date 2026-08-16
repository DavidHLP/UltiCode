package com.ulticode.modules.search.backfill;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SEARCH-003 backfill runner configuration (DEC-017). Defaults are inert:
 * the runner bean only exists when {@code app.search.backfill.enabled} and
 * {@code meilisearch.enabled} are both true.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.search.backfill")
public class SearchBackfillProperties {

    /** Comma-separated index selection (problems,users,posts,solutions); blank = all four. */
    private String indexes = "";

    /** Paging size for DB enumeration and Meili document reads. */
    private int pageSize = 500;
}
