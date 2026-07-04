# Build stage
FROM node:22-alpine AS builder

WORKDIR /app

# Install pnpm
RUN corepack enable && corepack prepare pnpm@9 --activate

# Copy package files
COPY management/package.json ./management/
COPY management/pnpm-lock.yaml ./management/

# Install dependencies
RUN corepack enable && corepack prepare pnpm@9 --activate && pnpm install --no-frozen-lockfile --dir ./management

# Copy source code
COPY management ./management

# Mirror the monorepo layout: management/ and shared/ are siblings. Locally
# management/src/shared is a symlink to ../../shared, which `COPY` cannot
# preserve (it follows symlinks and bakes shared/ into src/shared/). Place
# shared at the repo root and recreate the symlink so (a) tsconfig.app.json
# `../shared/auth-ui/...` resolves correctly and (b) the vite `@` alias
# (mapped to ./src) reaches shared/ via the symlink as it does on host.
COPY shared ./shared
RUN rm -rf /app/management/src/shared && \
    ln -sfn /app/shared /app/management/src/shared
# Give the shared/ packages a node_modules so their bare imports resolve from
# management's installed deps. The shared packages ship source only (peer deps
# provided by the host app), so in Docker they have no node_modules of their
# own — vue-tsc (type-check), @vue/compiler-sfc (.vue compile), and
# vite/rolldown all fail to resolve vue / reka-ui / vue-router / vue-i18n /
# clsx / etc. Symlinking shared/node_modules → management/node_modules fixes
# all three resolution paths via Node's standard upward node_modules lookup.
RUN ln -sfn /app/management/node_modules /app/shared/node_modules

# Build application
WORKDIR /app/management
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN pnpm build

# Production stage - nginx
FROM nginx:alpine AS production

# Create non-root user with specific UID for consistency
RUN addgroup -g 1001 -S appgroup && \
    adduser -S appuser -u 1001 -G appgroup

# Copy nginx config (listen on 8080 since non-root cannot bind port 80)
RUN rm -f /etc/nginx/conf.d/default.conf
COPY management/nginx.conf /etc/nginx/templates/default.conf.template
ENV API_ORIGIN="http://localhost:9001"

# Grant write access to nginx runtime directories and conf.d (for envsubst)
RUN chown -R appuser:appgroup /var/cache/nginx /var/run /var/log/nginx /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && \
    chown appuser:appgroup /var/run/nginx.pid

# Copy built application with proper ownership
COPY --from=builder --chown=appuser:appgroup /app/management/dist /usr/share/nginx/html

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

USER appuser

CMD ["nginx", "-g", "daemon off;"]
