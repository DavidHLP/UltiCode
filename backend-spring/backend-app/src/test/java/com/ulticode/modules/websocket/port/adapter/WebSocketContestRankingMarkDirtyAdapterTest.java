package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.app.api.service.ContestRankingMarkDirtyPort;
import com.ulticode.modules.websocket.notification.WebSocketContestRankingFlusher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Post-Candidate-4: the adapter delegates {@code markDirty} to
 * {@link WebSocketContestRankingFlusher} (the only producer-side
 * component that owns the throttle + flush + cleanup logic). Test pins
 * that delegation contract.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketContestRankingMarkDirtyAdapter")
class WebSocketContestRankingMarkDirtyAdapterTest {

    @Mock
    private WebSocketContestRankingFlusher flusher;

    @InjectMocks
    private WebSocketContestRankingMarkDirtyAdapter adapter;

    @Test
    @DisplayName("markDirty delegates to WebSocketContestRankingFlusher.markDirty")
    void markDirty_delegatesToFlusher() {
        adapter.markDirty("c-1");
        verify(flusher).markDirty("c-1");
        verifyNoMoreInteractions(flusher);
    }
}