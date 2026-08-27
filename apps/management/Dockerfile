# Build stage
FROM node:22-alpine AS builder

WORKDIR /app

# Install pnpm
RUN corepack enable && corepack prepare pnpm@9 --activate

COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
COPY apps/management/package.json ./apps/management/
COPY packages ./packages

RUN corepack enable && corepack prepare pnpm@9 --activate && \
    pnpm install --no-frozen-lockfile

# Copy source code
COPY apps/management ./apps/management

# Mirror the monorepo layout: management/ and shared/ are siblings. Locally
# management/src/shared is a symlink to ../../shared, which `COPY` cannot
# preserve (it follows symlinks and bakes shared/ into src/shared/). Place
# packages at the repo root and recreate the symlink so the app resolves
# shared package imports through the current monorepo layout.
COPY packages ./packages
RUN rm -rf /app/apps/management/src/shared && \
    ln -sfn /app/packages /app/apps/management/src/shared
# Give the shared/ packages a node_modules so their bare imports resolve from
# management's installed deps. The shared packages ship source only (peer deps
# provided by the host app), so in Docker they have no node_modules of their
# own — vue-tsc (type-check), @vue/compiler-sfc (.vue compile), and
# vite/rolldown all fail to resolve vue / reka-ui / vue-router / vue-i18n /
# clsx / etc. Symlinking shared/node_modules → management/node_modules fixes
# all three resolution paths via Node's standard upward node_modules lookup.
RUN ln -sfn /app/apps/management/node_modules /app/packages/node_modules

# Build application
WORKDIR /app/apps/management
ARG VITE_API_BASE_URL=/api
RUN mkdir -p /app/node_modules/@ulticode && \
    ln -sfn /app/packages/auth-core /app/node_modules/@ulticode/auth-core && \
    ln -sfn /app/packages/sandbox-types /app/node_modules/@ulticode/sandbox-types && \
    ln -sfn /app/packages/badge-config /app/node_modules/@ulticode/badge-config
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN pnpm build

# Production stage - nginx
FROM nginx:alpine AS production

# Create non-root user with specific UID for consistency
RUN addgroup -g 1001 -S appgroup && \
    adduser -S appuser -u 1001 -G appgroup

# Copy nginx config (listen on 8080 since non-root cannot bind port 80)
# Drop the alpine default conf so only ours loads.
RUN rm -f /etc/nginx/conf.d/default.conf
COPY apps/management/nginx.conf /etc/nginx/templates/default.conf.template
# Keep the shared security headers under templates/includes so the nginx
# entrypoint renders them into the conf.d/includes path used by nginx.conf.
COPY infrastructure/nginx/includes/security-headers.conf /etc/nginx/templates/includes/security-headers.conf.template
COPY infrastructure/nginx/includes/backend-proxy.conf /etc/nginx/conf.d/includes/backend-proxy.conf

# Grant write access to nginx runtime directories and conf.d (for envsubst)
RUN chown -R appuser:appgroup /var/cache/nginx /var/run /var/log/nginx /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && \
    chown appuser:appgroup /var/run/nginx.pid

# Copy built application with proper ownership
COPY --from=builder --chown=appuser:appgroup /app/apps/management/dist /usr/share/nginx/html

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

USER appuser

CMD ["nginx", "-g", "daemon off;"]
