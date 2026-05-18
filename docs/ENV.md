# Environment Variables

Reference for all environment variables used in UltiCode.

---

## AUTO-GENERATED: From .env.example

<!-- Source: .env.example -->

| Variable | Required | Description | Default |
|----------|----------|-------------|---------|
| `DATABASE_URL` | Yes | MySQL connection URL | `mysql://user:pass@localhost:23306/ulticode` |
| `DB_HOST` | Yes | MySQL host | `localhost` |
| `DB_PORT` | Yes | MySQL port | `23306` |
| `DB_USER` | Yes | MySQL username | `ulticode` |
| `DB_PASSWORD` | Yes | MySQL password | - |
| `DB_NAME` | Yes | Database name | `ulticode` |
| `MYSQL_ROOT_PASSWORD` | Yes | Root password | - |
| `JWT_SECRET` | Yes | JWT signing key (min 32 chars) | - |
| `JWT_COOKIE_SECURE` | No | Secure cookie flag | `false` (dev) / `true` (prod) |
| `CORS_ALLOWED_ORIGINS` | Yes | CORS origins (comma-separated) | `http://localhost:9002,http://localhost:9003` |
| `REDIS_HOST` | Yes | Redis host | `localhost` |
| `REDIS_PORT` | Yes | Redis port | `26379` |
| `REDIS_PASSWORD` | Yes | Redis password | - |
| `JUDGE_CONTAINER_ENABLED` | No | Enable code execution | `true` |
| `JUDGE_CONTAINER_IMAGE` | No | Docker image | `ulticode-judge:latest` |
| `JUDGE_CONTAINER_POOL_SIZE` | No | Pool size | `5` |
| `JUDGE_CONTAINER_MAX_CONTAINERS` | No | Max containers | `10` |
| `JUDGE_DEFAULT_TIME_LIMIT` | No | Time limit (ms) | `2000` |
| `JUDGE_DEFAULT_MEMORY_LIMIT` | No | Memory limit (MB) | `256` |
| `DOCKER_SOCKET_PATH` | No | Docker socket path | `/var/run/docker.sock` |
| `NACOS_SERVER_ADDR` | Yes | Nacos address | `localhost:28848` |
| `NACOS_HOST` | Yes | Nacos host | `localhost` |
| `NACOS_PORT` | Yes | Nacos port | `28848` |
| `NACOS_NAMESPACE` | No | Nacos namespace | `public` |
| `NACOS_GROUP` | No | Nacos group | `DEFAULT_GROUP` |
| `RECOMMENDATION_ENABLED` | No | Enable recommendations | `true` |
| `RECOMMENDATION_SERVICE_NAME` | No | Service name | `recommend-web` |
| `RECOMMENDATION_TIMEOUT` | No | Timeout (ms) | `5000` |
| `RECOMMENDATION_FALLBACK_URL` | No | Fallback URL | `http://localhost:28081` |
| `GITHUB_CLIENT_ID` | Optional | GitHub OAuth client ID | - |
| `GITHUB_CLIENT_SECRET` | Optional | GitHub OAuth secret | - |
| `GITHUB_REDIRECT_URI` | No | GitHub OAuth callback | `http://localhost:9001/auth/github/callback` |
| `GOOGLE_CLIENT_ID` | Optional | Google OAuth client ID | - |
| `GOOGLE_CLIENT_SECRET` | Optional | Google OAuth secret | - |
| `GOOGLE_REDIRECT_URI` | No | Google OAuth callback | `http://localhost:9001/auth/google/callback` |
| `STRIPE_SECRET_KEY` | Optional | Stripe secret key | - |
| `STRIPE_WEBHOOK_SECRET` | Optional | Stripe webhook secret | - |
| `STRIPE_PRICE_PREMIUM_MONTHLY` | Optional | Monthly price ID | - |
| `STRIPE_PRICE_PREMIUM_YEARLY` | Optional | Yearly price ID | - |
| `SMTP_HOST` | Optional | SMTP host | `smtp.example.com` |
| `SMTP_PORT` | Optional | SMTP port | `587` |
| `SMTP_USER` | Optional | SMTP username | - |
| `SMTP_PASSWORD` | Optional | SMTP password | - |
| `EMAIL_ENABLED` | No | Enable email | `false` |
| `VITE_API_BASE_URL` | Yes | API base URL | `http://localhost:9001` |
| `VITE_TEST_USERNAME` | Optional | Test username | - |
| `VITE_TEST_PASSWORD` | Optional | Test password | - |

<!-- END AUTO-GENERATED -->

---

## Security Notes

### Production Checklist

- [ ] All `CHANGE_ME_*` passwords replaced with strong values
- [ ] `JWT_SECRET` is at least 32 characters
- [ ] `JWT_COOKIE_SECURE=true` for HTTPS
- [ ] `CORS_ALLOWED_ORIGINS` set to production domains
- [ ] OAuth secrets from secure source
- [ ] Stripe keys from secure source

### Development vs Production

| Variable | Development | Production |
|----------|-------------|------------|
| `JWT_COOKIE_SECURE` | `false` | `true` |
| `CORS_ALLOWED_ORIGINS` | `localhost:9002,localhost:9003` | Actual domains |
| `EMAIL_ENABLED` | `false` | `true` (with real SMTP) |
