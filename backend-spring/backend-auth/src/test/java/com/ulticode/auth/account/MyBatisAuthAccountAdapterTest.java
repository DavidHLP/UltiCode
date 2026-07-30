package com.ulticode.auth.account;

import com.ulticode.auth.account.entity.AuthAccountEntity;
import com.ulticode.auth.account.mapper.AuthAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisAuthAccountAdapterTest {

    @Mock
    private AuthAccountMapper mapper;

    @InjectMocks
    private MyBatisAuthAccountAdapter adapter;

    private AuthAccountEntity sampleEntity;

    @BeforeEach
    void setUp() {
        sampleEntity = new AuthAccountEntity();
        sampleEntity.setId("user-1");
        sampleEntity.setUsername("alice");
        sampleEntity.setEmail("alice@example.com");
        sampleEntity.setPassword("hash");
        sampleEntity.setRole("USER");
        sampleEntity.setActive(true);
        sampleEntity.setBanned(false);
        sampleEntity.setJoinedAt(LocalDateTime.now());
        sampleEntity.setAuthzVersion(5L);
    }

    @Test
    @DisplayName("findById returns AuthAccountRecord with authzVersion")
    void findByIdReturnsRecordWithVersion() {
        when(mapper.findById("user-1")).thenReturn(sampleEntity);

        Optional<AuthAccountRecord> result = adapter.findById("user-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("user-1");
        assertThat(result.get().username()).isEqualTo("alice");
        assertThat(result.get().authzVersion()).isEqualTo(5L);
    }

    @Test
    @DisplayName("findByIds performs batch lookup and deduplication")
    void findByIdsPerformsBatchLookup() {
        when(mapper.findByIds(Set.of("user-1", "user-2"))).thenReturn(List.of(sampleEntity));

        List<AuthAccountRecord> result = adapter.findByIds(Set.of("user-1", "user-2", ""));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("updateAccountIfVersion returns true on CAS match and false on mismatch")
    void updateAccountIfVersionReturnsCasOutcome() {
        when(mapper.updateAccountIfVersion("user-1", true, false, "ADMIN", 5L)).thenReturn(1);
        when(mapper.updateAccountIfVersion("user-1", true, false, "ADMIN", 4L)).thenReturn(0);

        boolean success = adapter.updateAccountIfVersion("user-1", true, false, "ADMIN", 5L);
        boolean stale = adapter.updateAccountIfVersion("user-1", true, false, "ADMIN", 4L);

        assertThat(success).isTrue();
        assertThat(stale).isFalse();
    }
}
