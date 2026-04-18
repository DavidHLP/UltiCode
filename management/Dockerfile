# Build stage
FROM node:22-alpine AS builder

WORKDIR /app

# Install pnpm
RUN corepack enable && corepack prepare pnpm@9 --activate

# Copy package files
COPY management/package.json ./management/
COPY management/pnpm-lock.yaml ./management/

# Install dependencies
RUN corepack enable && corepack prepare pnpm@9 --activate && pnpm install --frozen-lockfile

# Copy source code
COPY management ./management

# Build application
WORKDIR /app/management
RUN pnpm build

# Production stage - nginx
FROM nginx:alpine AS production

# Create non-root user with specific UID for consistency
RUN addgroup -g 1001 -S appgroup && \
    adduser -S appuser -u 1001 -G appgroup

# Copy nginx config (listen on 8080 since non-root cannot bind port 80)
COPY management/nginx.conf /etc/nginx/templates/default.conf.template
RUN sed -i 's/listen 80;/listen 8080;/' /etc/nginx/templates/default.conf.template
ENV API_ORIGIN="http://localhost:9001"

# Grant write access to nginx runtime directories
RUN chown -R appuser:appgroup /var/cache/nginx /var/run /var/log/nginx && \
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
