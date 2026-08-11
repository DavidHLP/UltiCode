package com.ulticode.app.userprofile.provider;

import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultUserReadPortTest {

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private IdentityQueryService identityQueryService;

    private DefaultUserReadPort userReadPort;

    @BeforeEach
    void setUp() {
        userReadPort = new DefaultUserReadPort(userProfileMapper);
        ReflectionTestUtils.setField(userReadPort, "identityQueryService", identityQueryService);
    }

    @Test
    @DisplayName("resolves ALL recipients from Auth active-account contract")
    void resolvesAllRecipientsFromAuth() {
        when(identityQueryService.findActiveAccountIds())
                .thenReturn(RpcResult.success(List.of("user-1", "user-2", "user-1"), "t-1"));

        assertThat(userReadPort.findAllActiveIds()).containsExactly("user-1", "user-2");
    }

    @Test
    @DisplayName("fails closed when Auth active-account lookup throws")
    void failsClosedWhenAuthLookupThrows() {
        when(identityQueryService.findActiveAccountIds())
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertThatThrownBy(userReadPort::findAllActiveIds)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Auth active-account lookup failed");
    }
}
