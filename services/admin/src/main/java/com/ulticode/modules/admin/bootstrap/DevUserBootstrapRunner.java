package com.ulticode.modules.admin.bootstrap;

import com.ulticode.modules.admin.port.UserProvisioningPort;
import com.ulticode.modules.admin.port.UserProvisioningPort.AdministratorSpec;
import java.util.Optional;
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
 * intentionally easy to remember. Account materialization is delegated to
 * {@link UserProvisioningPort} (the user module's adapter); this runner owns only its development
 * identity policy (role whitelist, email-conflict refusal) and the CLI-only context shutdown.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dev-users.enabled", havingValue = "true")
public class DevUserBootstrapRunner implements ApplicationRunner {

  private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "SUPER_ADMIN");
  private static final String DEVELOPMENT_DISPLAY_NAME = "Development Administrator";

  private final UserProvisioningPort userProvisioningPort;
  private final Environment environment;
  private final ConfigurableApplicationContext applicationContext;

  @Override
  public void run(ApplicationArguments args) {
    if (!environment.acceptsProfiles(Profiles.of("dev"))) {
      throw new IllegalStateException("Development users may only be initialized in the dev profile");
    }

    String username = required("DEV_SEED_ADMIN_USERNAME");
    String email = required("DEV_SEED_ADMIN_EMAIL");
    String password = required("DEV_SEED_ADMIN_PASSWORD");
    required("BOOTSTRAP_DELEGATION_SECRET");
    String role = environment.getProperty("DEV_SEED_ADMIN_ROLE", "ADMIN").toUpperCase();
    if (!ALLOWED_ROLES.contains(role)) {
      throw new IllegalStateException("DEV_SEED_ADMIN_ROLE must be ADMIN or SUPER_ADMIN");
    }

    Optional<String> existingId = userProvisioningPort.findIdByUsername(username);
    if (userProvisioningPort.emailConflicts(email, existingId.orElse(null))) {
      throw new IllegalStateException("DEV_SEED_ADMIN_EMAIL is already used by another account");
    }

    AdministratorSpec spec =
        new AdministratorSpec(username, DEVELOPMENT_DISPLAY_NAME, email, password, role);
    if (existingId.isEmpty()) {
      userProvisioningPort.createAdministrator(spec);
      log.info("Created development administrator account: {}", username);
    } else {
      userProvisioningPort.restoreAdministrator(existingId.get(), spec);
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
