package com.ulticode.modules.user.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.admin.port.UserProvisioningPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.auth.account.AuthAccountPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Locks the concentrated create/restore invariant relocated from the deleted
 * {@code AdministratorProvisioner}: ids and timestamps flow through the
 * {@code UuidGenerator} and {@code Clock} seams, passwords are encoded, and
 * active accounts are pinned to active + unbanned. The adapter is the user
 * module's implementation of admin's {@link UserProvisioningPort}.
 */
class UserProvisioningAdapterTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-07-17T10:15:30Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));

  @Test
  void createAppliesSeamIdsAndTimestampsAndEncodesPassword() {
    UserMapper userMapper = mock(UserMapper.class);
    AuthAccountPort accountPort = mock(AuthAccountPort.class);
    PasswordEncoderStub encoder = new PasswordEncoderStub();
    UserProvisioningAdapter adapter =
        new UserProvisioningAdapter(
            userMapper, accountPort, encoder, new FixedUuidGenerator("admin-id-1"), FIXED_CLOCK);

    adapter.createAdministrator(
        new UserProvisioningPort.AdministratorSpec(
            "root", "Root Admin", "root@example.com", "raw-secret", "SUPER_ADMIN"));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(accountPort).create(captor.capture());
    User persisted = captor.getValue();
    assertThat(persisted.getId()).isEqualTo("admin-id-1");
    assertThat(persisted.getUsername()).isEqualTo("root");
    assertThat(persisted.getName()).isEqualTo("Root Admin");
    assertThat(persisted.getEmail()).isEqualTo("root@example.com");
    assertThat(persisted.getPassword()).isEqualTo("encoded(raw-secret)");
    assertThat(persisted.getRole()).isEqualTo("SUPER_ADMIN");
    assertThat(persisted.getIsActive()).isTrue();
    assertThat(persisted.getIsBanned()).isFalse();
    assertThat(persisted.getIsDeleted()).isEqualTo(0);
    assertThat(persisted.getJoinedAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
    assertThat(encoder.lastEncoded).isEqualTo("raw-secret");
  }

  @Test
  void restoreRewritesActiveFieldsAndClearsBanMetadataWithoutNewIdOrTimestamp() {
    UserMapper userMapper = mock(UserMapper.class);
    AuthAccountPort accountPort = mock(AuthAccountPort.class);
    PasswordEncoderStub encoder = new PasswordEncoderStub();
    UserProvisioningAdapter adapter =
        new UserProvisioningAdapter(
            userMapper, accountPort, encoder, new FixedUuidGenerator("unused-id"), FIXED_CLOCK);

    User existing = new User();
    existing.setId("original-id");
    existing.setUsername("admin");
    existing.setJoinedAt(LocalDateTime.parse("2024-01-01T00:00:00"));
    existing.setIsDeleted(0);
    existing.setIsActive(false);
    existing.setIsBanned(true);
    existing.setBannedUntil(LocalDateTime.parse("2026-12-31T00:00:00"));
    existing.setBannedReason("Disabled seed account");
    when(userMapper.selectById("original-id")).thenReturn(existing);

    adapter.restoreAdministrator(
        "original-id",
        new UserProvisioningPort.AdministratorSpec(
            "admin", "Development Administrator", "admin@localhost.test", "admin123", "ADMIN"));

    verify(accountPort).updateBanStatus("original-id", false, null);
    verify(accountPort).updatePassword("original-id", "encoded(admin123)");
    verify(accountPort).updateAccountCredentials("original-id", "admin", "admin@localhost.test", "ADMIN");
  }

  @Test
  void restoreThrowsAndMutatesNothingWhenAccountVanished() {
    UserMapper userMapper = mock(UserMapper.class);
    AuthAccountPort accountPort = mock(AuthAccountPort.class);
    PasswordEncoderStub encoder = new PasswordEncoderStub();
    UserProvisioningAdapter adapter =
        new UserProvisioningAdapter(
            userMapper, accountPort, encoder, new FixedUuidGenerator("unused-id"), FIXED_CLOCK);
    when(userMapper.selectById("ghost-id")).thenReturn(null);

    assertThatThrownBy(
            () ->
                adapter.restoreAdministrator(
                    "ghost-id",
                    new UserProvisioningPort.AdministratorSpec(
                        "admin", "Dev Admin", "admin@localhost.test", "admin123", "ADMIN")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nonexistent administrator");

    verify(userMapper, never()).updateById(org.mockito.ArgumentMatchers.<User>any());
    // No cleartext processed for a vanished account — the stub records the last encode() input
    assertThat(encoder.lastEncoded).isNull();
  }

  @Test
  void administratorSpecToStringRedactsRawPassword() {
    UserProvisioningPort.AdministratorSpec spec =
        new UserProvisioningPort.AdministratorSpec(
            "root", "Root Admin", "root@example.com", "super-secret-123", "SUPER_ADMIN");

    String rendered = spec.toString();

    assertThat(rendered).doesNotContain("super-secret-123");
    assertThat(rendered).contains("<redacted>");
    // value equality is unaffected by the toString override (still compares all components)
    assertThat(spec)
        .isEqualTo(
            new UserProvisioningPort.AdministratorSpec(
                "root", "Root Admin", "root@example.com", "super-secret-123", "SUPER_ADMIN"));
  }

  /** Minimal fake that records the cleartext and returns a distinguishable encoded value. */
  private static final class PasswordEncoderStub implements org.springframework.security.crypto.password.PasswordEncoder {
    String lastEncoded;

    @Override
    public String encode(CharSequence rawPassword) {
      this.lastEncoded = rawPassword.toString();
      return "encoded(" + rawPassword + ")";
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
      return false;
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
      return false;
    }
  }
}
