package com.ulticode.modules.admin.bootstrap;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Deep module that owns the create-and-restore administrator invariant.
 *
 * <p>Both bootstrap runners previously duplicated id assignment, password
 * encoding, account-state setting, and the join timestamp, and both bypassed
 * the shared {@link UuidGenerator} and {@link Clock} seams by calling
 * {@code IdUtil.fastSimpleUUID()} and {@code LocalDateTime.now()} inline. That
 * left the two security-sensitive provisioning paths with divergent, untestable
 * timestamps and ids. This provisioner concentrates the invariant instead: ids
 * come from {@link UuidGenerator#newId()}, passwords are encoded here, active
 * accounts are pinned to active + unbanned, {@code joinedAt} is stamped via
 * {@link LocalDateTime#now(Clock)}, and persistence is owned in one place. The
 * runners keep only their differing identity policy (credential strength,
 * conflict rules, granted role) and the CLI-only context shutdown.
 *
 * <p>Visible only inside the bootstrap package: it has one cohesive responsibility
 * and no cross-module caller, so no public interface is warranted.
 */
@Component
@RequiredArgsConstructor
class AdministratorProvisioner {

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final UuidGenerator uuidGenerator;
  private final Clock clock;

  /**
   * Create and persist a brand-new active administrator.
   *
   * <p>The new-account stamps (id, username, {@code joinedAt}, not-deleted) are
   * applied through the project seams; the shared active-administrator fields
   * are applied by {@link #applyActiveAdministrator(User, String, String, String, String)}.
   *
   * @param username login name
   * @param name display name
   * @param email contact email
   * @param rawPassword cleartext password; encoded here and never stored or logged raw
   * @param role granted role ({@code ADMIN} or {@code SUPER_ADMIN})
   * @return the persisted user
   */
  User createAdministrator(
      String username, String name, String email, String rawPassword, String role) {
    User user = new User();
    user.setId(uuidGenerator.newId());
    user.setUsername(username);
    user.setIsDeleted(0);
    user.setJoinedAt(LocalDateTime.now(clock));
    applyActiveAdministrator(user, name, email, rawPassword, role);
    userMapper.insert(user);
    return user;
  }

  /**
   * Re-enable an existing administrator account with fresh credentials.
   *
   * <p>The persistent id, username, {@code joinedAt}, and deleted flag are kept
   * from {@code existing}; only the active-administrator fields and ban metadata
   * are rewritten. No new id or timestamp is generated, so neither seam is
   * needed on the restore path.
   *
   * @param existing the previously persisted account to restore
   * @param name display name
   * @param email contact email
   * @param rawPassword cleartext password; encoded here and never stored or logged raw
   * @param role granted role ({@code ADMIN} or {@code SUPER_ADMIN})
   * @return the restored user
   */
  User restoreAdministrator(
      User existing, String name, String email, String rawPassword, String role) {
    applyActiveAdministrator(existing, name, email, rawPassword, role);
    existing.setBannedUntil(null);
    existing.setBannedReason(null);
    userMapper.updateById(existing);
    return existing;
  }

  private void applyActiveAdministrator(
      User user, String name, String email, String rawPassword, String role) {
    user.setName(name);
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode(rawPassword));
    user.setRole(role);
    user.setIsActive(true);
    user.setIsBanned(false);
  }
}
