package com.ulticode.modules.search.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
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
 * Unit tests for {@link DefaultUserSearchReadPort}, the App-side adapter over
 * the owner-composed user search read port.
 */
@ExtendWith(MockitoExtension.class)
class DefaultUserSearchReadPortTest {

    @Mock
    private UserDirectoryQueryPort userDirectoryQueryPort;

    @InjectMocks
    private DefaultUserSearchReadPort port;
    @Test
    @DisplayName("blank query returns empty list without touching the directory port")
    void blankQuerySkipsDirectoryPort() {
        assertThat(port.searchForIndex("   ", 10)).isEmpty();
        assertThat(port.searchForIndex(null, 10)).isEmpty();
        verifyNoInteractions(userDirectoryQueryPort);
    }

    @Test
    @DisplayName("non-positive limit returns empty list without touching the directory port")
    void nonPositiveLimitSkipsDirectoryPort() {
        assertThat(port.searchForIndex("alice", 0)).isEmpty();
        assertThat(port.searchForIndex("alice", -3)).isEmpty();
        verifyNoInteractions(userDirectoryQueryPort);
    }

    @Test
    @DisplayName("maps directory rows to index DTOs preserving all fields")
    void mapsRowsToDtos() {
        UserSearchRow row = new UserSearchRow();
        row.setId("u-1");
        row.setUsername("alice");
        row.setName("Alice Example");
        row.setAvatar("/avatars/a.png");
        when(userDirectoryQueryPort.search("ali", 0, 5))
                .thenReturn(List.of(UserDirectoryRow.from(row)));

        List<UserIndexDTO> result = port.searchForIndex("ali", 5);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.accountId()).isEqualTo("u-1");
            assertThat(dto.username()).isEqualTo("alice");
            assertThat(dto.name()).isEqualTo("Alice Example");
            assertThat(dto.avatar()).isEqualTo("/avatars/a.png");
        });
    }

    @Test
    @DisplayName("empty directory result yields empty list")
    void emptyDirectoryResultYieldsEmptyList() {
        when(userDirectoryQueryPort.search("zzz", 0, 10)).thenReturn(List.of());
        assertThat(port.searchForIndex("zzz", 10)).isEmpty();
    }
}
