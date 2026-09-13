package com.ulticode.modules.submission.port;

import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.app.user.port.UserDirectoryProjection;
import com.ulticode.app.user.port.UserSummaryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSubmissionUserReadAdapterTest {

    @Mock
    private UserDirectoryProjection userDirectoryProjection;

    private DefaultSubmissionUserReadAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DefaultSubmissionUserReadAdapter(userDirectoryProjection);
    }

    @Test
    void usesDirectoryUsernameAlongsideAppProfileName() {
        UserSummaryView view = new UserSummaryView(
                "user-1", "auth-user", "Display Name", "auth@example.test", "avatar-url",
                null, null, null, LocalDateTime.MIN, null, null, null, "en-US", "USER", true, false, null);
        when(userDirectoryProjection.selectByIds(List.of("user-1")))
                .thenReturn(Map.of("user-1", view));

        Map<String, SubmissionUserReadPort.UserSummary> result = adapter.findAllById(List.of("user-1"));

        assertThat(result).containsKey("user-1");
        SubmissionUserReadPort.UserSummary summary = result.get("user-1");
        assertThat(summary.username()).isEqualTo("auth-user");
        assertThat(summary.name()).isEqualTo("Display Name");
        assertThat(summary.avatar()).isEqualTo("avatar-url");
    }
}
