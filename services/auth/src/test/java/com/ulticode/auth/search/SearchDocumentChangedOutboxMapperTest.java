package com.ulticode.auth.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class SearchDocumentChangedOutboxMapperTest {

    @Test
    void reclaimStaleClaimedUsesRawLessThanOperator() throws NoSuchMethodException {
        Update update = SearchDocumentChangedOutboxMapper.class
                .getMethod("reclaimStaleClaimed", int.class)
                .getAnnotation(Update.class);

        assertThat(update).isNotNull();
        assertThat(update.value()).hasSize(1);
        assertThat(update.value()[0])
                .contains("claimed_at < DATE_SUB")
                .doesNotContain("&lt;");
    }
}
