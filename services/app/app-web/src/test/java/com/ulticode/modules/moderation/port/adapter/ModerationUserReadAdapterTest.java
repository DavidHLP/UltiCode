package com.ulticode.modules.moderation.port.adapter;

import com.ulticode.app.user.port.UserReadMapper;
import com.ulticode.app.user.port.UserSummaryView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationUserReadAdapterTest {

    @Mock
    private UserReadMapper userReadMapper;

    @Test
    void findByIdsUsesTheOwnerComposedBatchFactsSeam() {
        Set<String> ids = Set.of("u-1", "u-2");
        when(userReadMapper.selectByIds(ids)).thenReturn(Map.of(
                "u-1", user("u-1", "alice"),
                "u-2", user("u-2", "bob")));

        ModerationUserReadAdapter adapter = new ModerationUserReadAdapter(userReadMapper);

        assertThat(adapter.findByIds(List.of("u-1", "u-2")))
                .containsOnlyKeys("u-1", "u-2")
                .extractingByKey("u-1")
                .extracting(info -> info.username())
                .isEqualTo("alice");
        verify(userReadMapper).selectByIds(ids);
        verify(userReadMapper, never()).selectById("u-1");
        verify(userReadMapper, never()).selectById("u-2");
    }

    private static UserSummaryView user(String id, String username) {
        return new UserSummaryView(
                id, username, null, null, null, null, null, null, null,
                null, null, null, null, "USER", true, false, null);
    }
}
