package com.ulticode.modules.moderation.port.adapter;

import com.ulticode.app.user.port.UserFactsProjection;
import com.ulticode.app.user.port.UserFactView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModerationUserReadAdapterWhitespaceCrTest {

    @Test
    void findByIdsTrimsWhitespaceAndHitsCanonical() {
        UserFactsProjection mapper = mock(UserFactsProjection.class);
        ModerationUserReadAdapter adapter = new ModerationUserReadAdapter(mapper);

        UserFactView canonical = new UserFactView("u-1", "alice", "Alice", null, null,
                null, null, null, true, false);
        when(mapper.findByIds(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            assertThat(arg.toString()).doesNotContain(" u-1 ");
            return Map.of("u-1", canonical);
        });

        Map result = adapter.findByIds(List.of(" u-1 "));
        assertThat(result).containsKey("u-1");
    }
}
