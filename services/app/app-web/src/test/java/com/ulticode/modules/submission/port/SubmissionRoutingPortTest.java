package com.ulticode.modules.submission.port;

import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.config.SubmissionRoutingProperties;
import com.ulticode.modules.submission.port.adapter.RemoteSubmissionFencePort;
import com.ulticode.modules.submission.port.adapter.RemoteSubmissionWritePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Submission local/remote routing")
class SubmissionRoutingPortTest {

    @Mock
    private DefaultSubmissionWritePort localWrite;

    @Mock
    private RemoteSubmissionWritePort remoteWrite;

    @Mock
    private ObjectProvider<RemoteSubmissionWritePort> remoteWriteProvider;

    @Mock
    private DefaultSubmissionFencePort localFence;

    @Mock
    private RemoteSubmissionFencePort remoteFence;

    @Mock
    private ObjectProvider<RemoteSubmissionFencePort> remoteFenceProvider;

    @Test
    @DisplayName("local mode never calls the remote provider")
    void localModeUsesLocalWriter() {
        SubmissionRoutingProperties properties = properties("local");
        CreateSubmissionDTO request = new CreateSubmissionDTO();
        SubmissionVO expected = new SubmissionVO();
        when(localWrite.submit("user-1", request)).thenReturn(expected);

        SubmissionWriteRoutingPort routing = new SubmissionWriteRoutingPort(
                localWrite, remoteWriteProvider, properties);

        assertThat(routing.submit("user-1", request)).isSameAs(expected);
        verify(localWrite).submit("user-1", request);
        verifyNoInteractions(remoteWriteProvider, remoteWrite);
    }

    @Test
    @DisplayName("remote mode delegates the fenced verdict to the single remote writer")
    void remoteModeUsesRemoteWriter() {
        SubmissionRoutingProperties properties = properties("remote");
        when(remoteWriteProvider.getIfAvailable()).thenReturn(remoteWrite);
        when(remoteWrite.updateSubmissionResultFenced(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 3L, "attempt-1"))
                .thenReturn(true);

        SubmissionWriteRoutingPort routing = new SubmissionWriteRoutingPort(
                localWrite, remoteWriteProvider, properties);

        assertThat(routing.updateSubmissionResultFenced(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 3L, "attempt-1"))
                .isTrue();
        verify(remoteWrite).updateSubmissionResultFenced(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 3L, "attempt-1");
        verifyNoInteractions(localWrite);
    }

    @Test
    @DisplayName("remote mode keeps contest submissions on the local transaction")
    void remoteModeRoutesContestSubmissionsLocally() {
        SubmissionRoutingProperties properties = properties("remote");
        CreateSubmissionDTO contestRequest = new CreateSubmissionDTO();
        contestRequest.setContestId("contest-1");
        SubmissionVO expected = new SubmissionVO();
        when(localWrite.submit("user-1", contestRequest)).thenReturn(expected);

        SubmissionWriteRoutingPort routing = new SubmissionWriteRoutingPort(
                localWrite, remoteWriteProvider, properties);

        // Contest admission holds the contest row FOR UPDATE in the caller's
        // transaction; RPC + re-lock in a second transaction deadlocks
        // (CR P1-2), so the routing port must never send contest submissions
        // over the remote route.
        assertThat(routing.submit("user-1", contestRequest)).isSameAs(expected);
        verify(localWrite).submit("user-1", contestRequest);
        verifyNoInteractions(remoteWriteProvider, remoteWrite);
    }

    @Test
    @DisplayName("remote mode routes ordinary submissions to the remote writer")
    void remoteModeRoutesOrdinarySubmitRemotely() {
        SubmissionRoutingProperties properties = properties("remote");
        CreateSubmissionDTO request = new CreateSubmissionDTO();
        SubmissionVO expected = new SubmissionVO();
        when(remoteWriteProvider.getIfAvailable()).thenReturn(remoteWrite);
        when(remoteWrite.submit("user-1", request)).thenReturn(expected);

        SubmissionWriteRoutingPort routing = new SubmissionWriteRoutingPort(
                localWrite, remoteWriteProvider, properties);

        assertThat(routing.submit("user-1", request)).isSameAs(expected);
        verify(remoteWrite).submit("user-1", request);
        verifyNoInteractions(localWrite);
    }

    @Test
    @DisplayName("remote mode fails closed when the provider is unavailable")
    void remoteModeFailsClosedWithoutProvider() {
        SubmissionRoutingProperties properties = properties("remote");
        when(remoteWriteProvider.getIfAvailable()).thenReturn(null);

        SubmissionWriteRoutingPort routing = new SubmissionWriteRoutingPort(
                localWrite, remoteWriteProvider, properties);

        assertThatThrownBy(() -> routing.updateSubmissionResult(
                "sub-1", SubmissionStatus.ACCEPTED, 12, 1.5, "[]"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unavailable");
        verifyNoInteractions(localWrite, remoteWrite);
    }

    @Test
    @DisplayName("remote mode routes the generation fence as well")
    void remoteModeUsesRemoteFence() {
        SubmissionRoutingProperties properties = properties("remote");
        when(remoteFenceProvider.getIfAvailable()).thenReturn(remoteFence);
        when(remoteFence.currentGeneration("sub-1")).thenReturn(Optional.of(3L));

        SubmissionFenceRoutingPort routing = new SubmissionFenceRoutingPort(
                localFence, remoteFenceProvider, properties);

        assertThat(routing.currentGeneration("sub-1")).contains(3L);
        verify(remoteFence).currentGeneration("sub-1");
        verifyNoInteractions(localFence);
    }

    private SubmissionRoutingProperties properties(String mode) {
        SubmissionRoutingProperties properties = new SubmissionRoutingProperties();
        properties.setMode(mode);
        return properties;
    }
}
