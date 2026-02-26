# Build stage
FROM node:22-alpine AS builder

WORKDIR /app

# Install pnpm
RUN corepack enable && corepack prepare pnpm@9 --activate

# Copy root package files (for workspace)
COPY pnpm-lock.yaml package.json pnpm-workspace.yaml* ./

# Copy console package files
COPY console/package.json ./console/

# Install dependencies
RUN pnpm install --filter edit-markdown --frozen-lockfile

# Copy source code
COPY console ./console

# Build application
WORKDIR /app/console
RUN pnpm build

# Production stage - nginx
FROM nginx:alpine AS production

# Copy nginx config
COPY console/nginx.conf /etc/nginx/conf.d/default.conf

# Copy built application from builder
COPY --from=builder /app/console/dist /usr/share/nginx/html

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
