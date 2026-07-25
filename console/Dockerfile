# Build stage
FROM node:22-alpine AS builder

WORKDIR /app

# Install pnpm
RUN corepack enable && corepack prepare pnpm@9 --activate

# Copy package files
COPY console/package.json ./console/
COPY console/pnpm-lock.yaml ./console/

# Install dependencies
RUN corepack enable && corepack prepare pnpm@9 --activate && pnpm install --no-frozen-lockfile --dir ./console

# Copy source code
COPY console ./console

# Mirror the monorepo layout: console/ and shared/ are siblings. Locally
# console/src/shared is a symlink to ../../shared, which `COPY` cannot
# preserve (it follows symlinks and bakes shared/ into src/shared/). Place
# shared at the repo root and recreate the symlink so (a) tsconfig.app.json
# `../shared/auth-ui/...` resolves correctly and (b) the vite `@` alias
# (mapped to ./src) reaches shared/ via the symlink as it does on host.
COPY shared ./shared
RUN rm -rf /app/console/src/shared && \
    ln -sfn /app/shared /app/console/src/shared
# Give the shared/ packages a node_modules so their bare imports (vue,
# vue-router, vue-i18n, reka-ui, clsx, ...) resolve from console's installed
# deps. The shared packages ship source only — peer deps are provided by the
# host app — so in Docker they have no node_modules of their own, and
# vue-tsc (type-check), @vue/compiler-sfc (.vue compile), AND vite/rolldown
# all fail to resolve those imports. Symlinking shared/node_modules →
# console/node_modules fixes all three resolution paths at once via Node's
# standard upward node_modules lookup.
RUN ln -sfn /app/console/node_modules /app/shared/node_modules

# Build application
WORKDIR /app/console
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN pnpm build

# Production stage - nginx
FROM nginx:alpine AS production

# Create non-root user with specific UID for consistency
RUN addgroup -g 1001 -S appgroup && \
    adduser -S appuser -u 1001 -G appgroup

# Copy nginx config (listen on 8080 since non-root cannot bind port 80).
# Drop the alpine default conf so only ours gets loaded.
RUN rm -f /etc/nginx/conf.d/default.conf
COPY console/nginx.conf /etc/nginx/templates/default.conf.template
# Security-headers.conf contains ${API_ORIGIN}; it MUST go through the
# envsubst pipeline, so copy it under templates/ as a .template and let
# the alpine entrypoint render it into /etc/nginx/conf.d/includes/.
COPY infrastructure/nginx/includes/security-headers.conf /etc/nginx/templates/includes/security-headers.conf.template
COPY infrastructure/nginx/includes/backend-proxy.conf /etc/nginx/conf.d/includes/backend-proxy.conf
ENV API_ORIGIN="http://localhost:9001"

# Grant write access to nginx runtime directories and conf.d (for envsubst)
RUN chown -R appuser:appgroup /var/cache/nginx /var/run /var/log/nginx /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && \
    chown appuser:appgroup /var/run/nginx.pid

# Copy built application with proper ownership
COPY --from=builder --chown=appuser:appgroup /app/console/dist /usr/share/nginx/html

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1

USER appuser

CMD ["nginx", "-g", "daemon off;"]
