package com.ulticode.modules.moderation.port.adapter;

import com.ulticode.app.user.port.UserReadMapper;
import com.ulticode.app.user.port.UserSummaryView;
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
        UserReadMapper mapper = mock(UserReadMapper.class);
        ModerationUserReadAdapter adapter = new ModerationUserReadAdapter(mapper);

        UserSummaryView canonical = new UserSummaryView("u-1", "alice", "Alice", "a@test.com", null,
                null, null, null, null, null, null, null, null, "USER", true, false, null);
        when(mapper.selectByIds(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            assertThat(arg.toString()).doesNotContain(" u-1 ");
            return Map.of("u-1", canonical);
        });

        Map result = adapter.findByIds(List.of(" u-1 "));
        assertThat(result).containsKey("u-1");
    }
}
