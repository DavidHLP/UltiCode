package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.websocket.service.RealtimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketContestRankingMarkDirtyAdapter")
class WebSocketContestRankingMarkDirtyAdapterTest {

    @Mock
    private RealtimeService realtimeService;

    @InjectMocks
    private WebSocketContestRankingMarkDirtyAdapter adapter;

    @Test
    @DisplayName("markDirty delegates to RealtimeService.markDirty with same contestId")
    void markDirty_delegates() {
        adapter.markDirty("c-1");
        verify(realtimeService).markDirty("c-1");
        verifyNoMoreInteractions(realtimeService);
    }
}