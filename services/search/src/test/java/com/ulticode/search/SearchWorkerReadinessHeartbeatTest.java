package com.ulticode.search;

import com.ulticode.search.adapter.SearchIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchWorkerReadinessHeartbeatTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private SearchIndex searchIndex;

    private SearchWorkerReadinessHeartbeat heartbeat;

    @BeforeEach
    void setUp() {
        heartbeat = new SearchWorkerReadinessHeartbeat(redisTemplate, searchIndex, null);
        when(redisTemplate.execute(any(org.springframework.data.redis.core.RedisCallback.class)))
                .thenReturn("PONG");
    }

    @Test
    void readinessUsesSearchIndexHealthSeam() {
        heartbeat.beat();

        verify(searchIndex).health();
    }
}
