package com.ulticode.app.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ulticode.app.user.port.UserDirectoryProjection;
import com.ulticode.app.user.port.UserSummaryView;
import com.ulticode.common.auth.AccountInfo;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountReadAdapterTest {

    @Mock
    private UserDirectoryProjection userDirectoryProjection;

    @Test
    void missingUserFailsClosed() {
        when(userDirectoryProjection.selectById("user-1")).thenReturn(null);

        assertThat(new AccountReadAdapter(userDirectoryProjection).findById("user-1"))
                .isEmpty();
    }

    @Test
    void existingUserMapsAccountFacts() {
        UserSummaryView user = new UserSummaryView(
                "user-1", "alice", "Alice", null, null, null, null, null,
                LocalDateTime.of(2026, 1, 1, 0, 0), null, null, null, "en-US",
                "ADMIN", true, false, null);
        when(userDirectoryProjection.selectById("user-1")).thenReturn(user);

        assertThat(new AccountReadAdapter(userDirectoryProjection).findById("user-1"))
                .contains(new AccountInfo("user-1", "alice", "ADMIN", true, false));
    }
}
