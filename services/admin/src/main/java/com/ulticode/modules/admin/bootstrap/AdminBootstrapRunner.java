package com.ulticode.modules.admin.bootstrap;

import com.ulticode.modules.admin.port.UserProvisioningPort;
import com.ulticode.modules.admin.port.UserProvisioningPort.AdministratorSpec;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Explicit one-time administrator bootstrap command.
 *
 * <p>Run the application as a non-web process with APP_BOOTSTRAP_ADMIN_ENABLED=true. The command
 * refuses to run when any active administrator already exists and never logs the password. Account
 * materialization (id, encoded password, account state, join timestamp) is delegated to
 * {@link UserProvisioningPort} (the user module's adapter); this runner owns only its production
 * identity policy (credential strength and conflict refusal) and the CLI-only context shutdown.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final Pattern STRONG_PASSWORD =
      Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{16,}$");

  private final UserProvisioningPort userProvisioningPort;
  private final Environment environment;
  private final ConfigurableApplicationContext applicationContext;

  @Override
  public void run(ApplicationArguments args) {
    String username = required("APP_BOOTSTRAP_ADMIN_USERNAME");
    String email = required("APP_BOOTSTRAP_ADMIN_EMAIL");
    String password = required("APP_BOOTSTRAP_ADMIN_PASSWORD");

    if (!EMAIL_PATTERN.matcher(email).matches()) {
      throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_EMAIL must be a valid email address");
    }
    if (!STRONG_PASSWORD.matcher(password).matches()) {
      throw new IllegalStateException(
          "APP_BOOTSTRAP_ADMIN_PASSWORD must be at least 16 characters and include upper, lower, "
              + "digit, and symbol characters");
    }

    if (userProvisioningPort.countActiveAdministrators() > 0) {
      throw new IllegalStateException("An active administrator already exists; bootstrap refused");
    }

    if (userProvisioningPort.identityExists(username, email)) {
      throw new IllegalStateException("Bootstrap username or email already exists; overwrite refused");
    }

    userProvisioningPort.createAdministrator(
        new AdministratorSpec(username, username, email, password, "SUPER_ADMIN"));

    log.info("Created bootstrap SUPER_ADMIN account: {}", username);
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
