package com.ulticode.modules.search.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.app.api.dto.UserIndexDTO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link DefaultUserSearchReadPort} — the App-side
 * Q-read adapter over the Auth-owned {@code users} table.
 */
@ExtendWith(MockitoExtension.class)
class DefaultUserSearchReadPortTest {

    @Mock
    private UserSearchReadMapper userSearchReadMapper;

    @InjectMocks
    private DefaultUserSearchReadPort port;

    @Test
    @DisplayName("blank query returns empty list without touching the mapper")
    void blankQuerySkipsMapper() {
        assertThat(port.searchForIndex("   ", 10)).isEmpty();
        assertThat(port.searchForIndex(null, 10)).isEmpty();
        verify(userSearchReadMapper, never()).searchIndex(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("non-positive limit returns empty list without touching the mapper")
    void nonPositiveLimitSkipsMapper() {
        assertThat(port.searchForIndex("alice", 0)).isEmpty();
        assertThat(port.searchForIndex("alice", -3)).isEmpty();
        verify(userSearchReadMapper, never()).searchIndex(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("maps rows to index DTOs preserving all fields")
    void mapsRowsToDtos() {
        UserSearchRow row = new UserSearchRow();
        row.setId("u-1");
        row.setUsername("alice");
        row.setName("Alice Example");
        row.setAvatar("/avatars/a.png");
        when(userSearchReadMapper.searchIndex("ali", 5)).thenReturn(List.of(row));

        List<UserIndexDTO> result = port.searchForIndex("ali", 5);

        assertThat(result).hasSize(1);
        UserIndexDTO dto = result.get(0);
        assertThat(dto.accountId()).isEqualTo("u-1");
        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.name()).isEqualTo("Alice Example");
        assertThat(dto.avatar()).isEqualTo("/avatars/a.png");
    }

    @Test
    @DisplayName("null avatar/name survive the mapping (nullable display columns)")
    void nullableFieldsSurviveMapping() {
        UserSearchRow row = new UserSearchRow();
        row.setId("u-2");
        row.setUsername("bob");
        when(userSearchReadMapper.searchIndex("bob", 10)).thenReturn(List.of(row));

        List<UserIndexDTO> result = port.searchForIndex("bob", 10);

        assertThat(result.get(0).name()).isNull();
        assertThat(result.get(0).avatar()).isNull();
    }

    @Test
    @DisplayName("empty mapper result yields empty list, never null")
    void emptyMapperResultYieldsEmptyList() {
        when(userSearchReadMapper.searchIndex("zzz", 10)).thenReturn(List.of());
        assertThat(port.searchForIndex("zzz", 10)).isEmpty();
    }
}
