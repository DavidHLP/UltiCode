package com.ulticode.app.userprofile.provider;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.user.port.UserProfileReadMapper;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserProfileQueryProviderTest {

    private UserProfileReadMapper profileReadMapper;
    private UserProfileQueryProvider provider;

    private UserProfileDTO sampleProfile;

    @BeforeEach
    void setUp() {
        profileReadMapper = mock(UserProfileReadMapper.class);
        provider = new UserProfileQueryProvider(profileReadMapper);

        sampleProfile = new UserProfileDTO(
                "user-100", "Alice", "https://avatar.com/100", "hello world",
                "Acme", "alice_gh", "Beijing", "alice_tw", "https://alice.com", "zh-CN");
    }

    @Test
    @DisplayName("getProfileByAccountId returns profile DTO when profile exists")
    void getProfileByAccountIdSuccess() {
        when(profileReadMapper.findByAccountId("user-100")).thenReturn(sampleProfile);

        RpcResult<UserProfileDTO> result = provider.getProfileByAccountId("user-100");

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(result.data().accountId()).isEqualTo("user-100");
        assertThat(result.data().name()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("getProfileByAccountId returns empty profile DTO when profile row is absent")
    void getProfileByAccountIdAbsentReturnsEmptyStub() {
        when(profileReadMapper.findByAccountId("user-missing")).thenReturn(null);

        RpcResult<UserProfileDTO> result = provider.getProfileByAccountId("user-missing");

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(result.data().accountId()).isEqualTo("user-missing");
        assertThat(result.data().name()).isNull();
    }

    @Test
    @DisplayName("getProfilesByAccountIds returns matching profiles and fills empty stubs for missing IDs")
    void getProfilesByAccountIdsSuccess() {
        when(profileReadMapper.findByAccountIds(anySet())).thenReturn(List.of(sampleProfile));

        RpcResult<List<UserProfileDTO>> result = provider.getProfilesByAccountIds(Set.of("user-100", "user-missing"));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).hasSize(2);
        assertThat(result.data()).extracting(UserProfileDTO::accountId)
                .containsExactlyInAnyOrder("user-100", "user-missing");
    }
}
