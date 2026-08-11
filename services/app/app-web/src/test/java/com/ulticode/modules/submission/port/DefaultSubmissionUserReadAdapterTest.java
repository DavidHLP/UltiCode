package com.ulticode.modules.submission.port;

import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSubmissionUserReadAdapterTest {

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private IdentityQueryService identityQueryService;

    private DefaultSubmissionUserReadAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DefaultSubmissionUserReadAdapter(userProfileMapper);
        ReflectionTestUtils.setField(adapter, "identityQueryService", identityQueryService);
    }

    @Test
    void preservesAuthUsernameAlongsideAppProfileName() {
        UserProfile profile = new UserProfile();
        profile.setAccountId("user-1");
        profile.setName("Display Name");
        profile.setAvatar("avatar-url");
        when(userProfileMapper.selectBatchIds(any())).thenReturn(List.of(profile));
        when(identityQueryService.batchGetIdentity(any()))
                .thenReturn(RpcResult.success(
                        List.of(new UserIdentityDTO("user-1", "auth-user", "USER", true, false)),
                        "trace-1"));

        Map<String, SubmissionUserReadPort.UserSummary> result = adapter.findAllById(List.of("user-1"));

        assertThat(result).containsKey("user-1");
        SubmissionUserReadPort.UserSummary summary = result.get("user-1");
        assertThat(summary.username()).isEqualTo("auth-user");
        assertThat(summary.name()).isEqualTo("Display Name");
        assertThat(summary.avatar()).isEqualTo("avatar-url");
    }
}
