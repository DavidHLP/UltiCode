package com.ulticode.modules.admin.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Creates or restores the documented local administrator for a development database.
 *
 * <p>This runner is unavailable outside the dev profile and must also be explicitly enabled. It
 * exists separately from the production-safe administrator bootstrap because local credentials are
 * intentionally easy to remember. Account materialization (id, encoded password, account state, join
 * timestamp, ban-clearing on restore) is delegated to {@link AdministratorProvisioner}; this runner
 * owns only its development identity policy (role whitelist, email-conflict refusal) and the CLI-only
 * context shutdown.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dev-users.enabled", havingValue = "true")
public class DevUserBootstrapRunner implements ApplicationRunner {

  private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "SUPER_ADMIN");
  private static final String DEVELOPMENT_DISPLAY_NAME = "Development Administrator";

  private final UserMapper userMapper;
  private final Environment environment;
  private final ConfigurableApplicationContext applicationContext;
  private final AdministratorProvisioner provisioner;

  @Override
  public void run(ApplicationArguments args) {
    if (!environment.acceptsProfiles(Profiles.of("dev"))) {
      throw new IllegalStateException("Development users may only be initialized in the dev profile");
    }

    String username = required("DEV_SEED_ADMIN_USERNAME");
    String email = required("DEV_SEED_ADMIN_EMAIL");
    String password = required("DEV_SEED_ADMIN_PASSWORD");
    String role = environment.getProperty("DEV_SEED_ADMIN_ROLE", "ADMIN").toUpperCase();
    if (!ALLOWED_ROLES.contains(role)) {
      throw new IllegalStateException("DEV_SEED_ADMIN_ROLE must be ADMIN or SUPER_ADMIN");
    }

    User user =
        userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, username).last("LIMIT 1"));

    Long emailConflicts =
        userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .ne(user != null, User::getId, user == null ? null : user.getId()));
    if (emailConflicts > 0) {
      throw new IllegalStateException("DEV_SEED_ADMIN_EMAIL is already used by another account");
    }

    if (user == null) {
      provisioner.createAdministrator(username, DEVELOPMENT_DISPLAY_NAME, email, password, role);
      log.info("Created development administrator account: {}", username);
    } else {
      provisioner.restoreAdministrator(user, DEVELOPMENT_DISPLAY_NAME, email, password, role);
      log.info("Restored development administrator account: {}", username);
    }
    applicationContext.close();
  }

  private String required(String key) {
    String value = environment.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(key + " is required");
    }
    return value;
  }
}
