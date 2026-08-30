package com.ulticode.modules.submission.port;

import com.ulticode.modules.submission.config.SubmissionRoutingProperties;
import com.ulticode.modules.submission.port.adapter.LocalSubmissionUserQueryAdapter;
import com.ulticode.modules.submission.port.adapter.RemoteSubmissionUserQueryAdapter;
import com.ulticode.submission.api.service.SubmissionUserQueryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Submission read routing")
class SubmissionRoutingPortTest {

    @Mock
    private LocalSubmissionUserQueryAdapter localUserQuery;

    @Mock
    private RemoteSubmissionUserQueryAdapter remoteUserQuery;

    @Mock
    private ObjectProvider<RemoteSubmissionUserQueryAdapter> remoteUserQueryProvider;

    @Test
    @DisplayName("local mode keeps user reads in App")
    void localModeUsesLocalUserQuery() {
        SubmissionRoutingProperties properties = properties(SubmissionRoutingProperties.LOCAL);
        when(localUserQuery.aggregateDates("user-1", 2026))
                .thenReturn(java.util.List.of("2026-08-20"));

        SubmissionUserQueryPort routing = new SubmissionUserQueryRoutingPort(
                localUserQuery, remoteUserQueryProvider, properties);

        assertThat(routing.aggregateDates("user-1", 2026)).containsExactly("2026-08-20");
        verify(localUserQuery).aggregateDates("user-1", 2026);
        verifyNoInteractions(remoteUserQueryProvider, remoteUserQuery);
    }

    @Test
    @DisplayName("remote mode reads user submission facts from the owner")
    void remoteModeUsesRemoteUserQuery() {
        SubmissionRoutingProperties properties = properties(SubmissionRoutingProperties.REMOTE);
        when(remoteUserQueryProvider.getIfAvailable()).thenReturn(remoteUserQuery);
        when(remoteUserQuery.aggregateDates("user-1", 2026))
                .thenReturn(java.util.List.of("2026-08-20"));

        SubmissionUserQueryPort routing = new SubmissionUserQueryRoutingPort(
                localUserQuery, remoteUserQueryProvider, properties);

        assertThat(routing.aggregateDates("user-1", 2026)).containsExactly("2026-08-20");
        verify(remoteUserQuery).aggregateDates("user-1", 2026);
        verifyNoInteractions(localUserQuery);
    }

    private SubmissionRoutingProperties properties(String mode) {
        SubmissionRoutingProperties properties = new SubmissionRoutingProperties();
        properties.setMode(mode);
        properties.setCutoverComplete(SubmissionRoutingProperties.REMOTE.equals(mode));
        return properties;
    }
}
