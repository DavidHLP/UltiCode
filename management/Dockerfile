# Build stage
FROM node:22-alpine AS builder

WORKDIR /app

# Install pnpm
RUN corepack enable && corepack prepare pnpm@9 --activate

# Copy root package files (for workspace)
COPY pnpm-lock.yaml package.json pnpm-workspace.yaml* ./

# Copy management package files
COPY management/package.json ./management/

# Install dependencies
RUN pnpm install --filter admin-frontend --frozen-lockfile

# Copy source code
COPY management ./management

# Build application
WORKDIR /app/management
RUN pnpm build

# Production stage - nginx
FROM nginx:alpine AS production

# Copy nginx config
COPY management/nginx.conf /etc/nginx/conf.d/default.conf

# Copy built application from builder
COPY --from=builder /app/management/dist /usr/share/nginx/html

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
