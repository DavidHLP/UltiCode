package com.ulticode.submission.api.catalog;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.submission.api.dto.SubmissionStatusMeta;
import org.junit.jupiter.api.Test;

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionStatusCatalogTest {

    @Test
    void containsOneEntryAndProjectionForEveryDurableStatus() {
        assertThat(StreamSupport.stream(SubmissionStatusCatalog.entries().spliterator(), false))
                .hasSize(SubmissionStatus.values().length);

        SubmissionStatusMeta accepted = SubmissionStatusCatalog.toMeta(SubmissionStatus.ACCEPTED);
        assertThat(accepted.getKey()).isEqualTo("Accepted");
        assertThat(accepted.getCode()).isEqualTo("ACCEPTED");
        assertThat(accepted.getDescription()).isEqualTo("All test cases passed");
        assertThat(accepted.getSeverity()).isEqualTo("success");
        assertThat(accepted.getIsTerminal()).isTrue();
        assertThat(accepted.getSortOrder()).isEqualTo(2);
    }

    @Test
    void unknownStatusInputUsesTheDefensiveCatalogDefault() {
        assertThat(SubmissionStatusCatalog.forStatus(null))
                .extracting(SubmissionStatusCatalog.Entry::severity,
                        SubmissionStatusCatalog.Entry::sortOrder)
                .containsExactly("info", Integer.MAX_VALUE);
    }
}
