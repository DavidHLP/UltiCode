package com.ulticode.modules.moderation.port.adapter;

import com.ulticode.app.user.port.UserFactView;
import com.ulticode.app.user.port.UserFactsProjection;
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
    private UserFactsProjection userFactsProjection;

    @Test
    void findByIdsUsesTheOwnerComposedBatchFactsSeam() {
        Set<String> ids = Set.of("u-1", "u-2");
        when(userFactsProjection.findByIds(ids)).thenReturn(Map.of(
                "u-1", user("u-1", "alice"),
                "u-2", user("u-2", "bob")));

        ModerationUserReadAdapter adapter = new ModerationUserReadAdapter(userFactsProjection);

        assertThat(adapter.findByIds(List.of("u-1", "u-2")))
                .containsOnlyKeys("u-1", "u-2")
                .extractingByKey("u-1")
                .extracting(info -> info.username())
                .isEqualTo("alice");
        verify(userFactsProjection).findByIds(ids);
        verify(userFactsProjection, never()).findById("u-1");
        verify(userFactsProjection, never()).findById("u-2");
    }

    private static UserFactView user(String id, String username) {
        return new UserFactView(
                id, username, null, null, null, null, null, null, true, false);
    }
}
