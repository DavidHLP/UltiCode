# Security Remediation Runbook

## Credential Rotation

1. Generate new database, Redis, JWT, OAuth, SMTP, and Nacos secrets in the deployment secret store.
2. Rotate the database application user and update deployment secrets atomically.
3. Set `JWT_COOKIE_SECURE=true` and deploy the new JWT secret. This intentionally invalidates all existing sessions.
4. Start MySQL, then provision Nacos:

   ```bash
   MYSQL_ROOT_PASSWORD=... NACOS_USERNAME=... NACOS_PASSWORD=... \
     ./scripts/security/bootstrap-nacos-user.sh
   ```

5. Deploy with unique `NACOS_AUTH_TOKEN`, `NACOS_AUTH_IDENTITY_KEY`, and
   `NACOS_AUTH_IDENTITY_VALUE` values.
6. Verify that only the external TLS gateway is publicly reachable.

## Initial Administrator

After migrations lock the documented seed accounts, create one administrator:

```bash
APP_BOOTSTRAP_ADMIN_ENABLED=true \
APP_BOOTSTRAP_ADMIN_USERNAME=... \
APP_BOOTSTRAP_ADMIN_EMAIL=... \
APP_BOOTSTRAP_ADMIN_PASSWORD=... \
java -jar backend-spring/target/app.jar \
  --spring.main.web-application-type=none
```

Remove the bootstrap variables after success. The command refuses to overwrite an
identity or run while an active administrator exists.

## Git History Cleanup

Run this in a fresh maintenance clone after rotating credentials:

```bash
git filter-repo \
  --path backend-spring/start-backend.sh \
  --path backend-spring/ecosystem.config.cjs \
  --path init-db/sql/20260530_ulticode_dump.sql \
  --invert-paths
```

Inspect all rewritten refs with Gitleaks. Force-pushing branches and tags requires
repository-owner approval and a coordinated maintenance window. Afterwards, revoke
old pull-request refs where possible and require collaborators to re-clone or reset
onto the rewritten history.
