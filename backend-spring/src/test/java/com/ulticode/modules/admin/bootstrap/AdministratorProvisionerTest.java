package com.ulticode.modules.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Locks the concentrated create/restore invariant: ids and timestamps flow through the
 * {@code UuidGenerator} and {@code Clock} seams (the bypass the runners previously had), passwords
 * are encoded, and active accounts are pinned to active + unbanned.
 */
class AdministratorProvisionerTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-07-17T10:15:30Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));

  @Test
  void createAppliesSeamIdsAndTimestampsAndEncodesPassword() {
    UserMapper userMapper = mock(UserMapper.class);
    PasswordEncoderStub encoder = new PasswordEncoderStub();
    AdministratorProvisioner provisioner =
        new AdministratorProvisioner(
            userMapper, encoder, new FixedUuidGenerator("admin-id-1"), FIXED_CLOCK);

    User created =
        provisioner.createAdministrator(
            "root", "Root Admin", "root@example.com", "raw-secret", "SUPER_ADMIN");

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userMapper).insert(captor.capture());
    User persisted = captor.getValue();
    assertThat(persisted).isSameAs(created);
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
    PasswordEncoderStub encoder = new PasswordEncoderStub();
    AdministratorProvisioner provisioner =
        new AdministratorProvisioner(
            userMapper, encoder, new FixedUuidGenerator("unused-id"), FIXED_CLOCK);

    User existing = new User();
    existing.setId("original-id");
    existing.setUsername("admin");
    existing.setJoinedAt(LocalDateTime.parse("2024-01-01T00:00:00"));
    existing.setIsDeleted(0);
    existing.setIsActive(false);
    existing.setIsBanned(true);
    existing.setBannedUntil(LocalDateTime.parse("2026-12-31T00:00:00"));
    existing.setBannedReason("Disabled seed account");

    User restored =
        provisioner.restoreAdministrator(
            existing, "Development Administrator", "admin@localhost.test", "admin123", "ADMIN");

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userMapper).updateById(captor.capture());
    assertThat(restored).isSameAs(existing);
    User persisted = captor.getValue();
    assertThat(persisted.getId()).isEqualTo("original-id");
    assertThat(persisted.getUsername()).isEqualTo("admin");
    assertThat(persisted.getJoinedAt()).isEqualTo(LocalDateTime.parse("2024-01-01T00:00:00"));
    assertThat(persisted.getPassword()).isEqualTo("encoded(admin123)");
    assertThat(persisted.getRole()).isEqualTo("ADMIN");
    assertThat(persisted.getIsActive()).isTrue();
    assertThat(persisted.getIsBanned()).isFalse();
    assertThat(persisted.getBannedUntil()).isNull();
    assertThat(persisted.getBannedReason()).isNull();
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
