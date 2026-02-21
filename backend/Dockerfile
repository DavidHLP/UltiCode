# Build stage
FROM node:22-alpine AS builder

WORKDIR /app

# Install pnpm
RUN corepack enable && corepack prepare pnpm@9 --activate

# Copy root package files (for workspace)
COPY pnpm-lock.yaml package.json pnpm-workspace.yaml* ./

# Copy backend package files
COPY backend/package.json ./backend/

# Install dependencies
RUN pnpm install --filter backend --frozen-lockfile

# Copy prisma schema and source code
COPY backend/prisma ./backend/prisma/
COPY backend/src ./backend/src/
COPY backend/tsconfig.json ./backend/
COPY backend/tsconfig.build.json ./backend/
COPY backend/nest-cli.json ./backend/

# Generate Prisma client and build
WORKDIR /app/backend
RUN pnpm prisma:generate && pnpm build

# Production stage
FROM node:22-alpine AS production

WORKDIR /app

# Install pnpm
RUN corepack enable && corepack prepare pnpm@9 --activate

# Copy root package files
COPY pnpm-lock.yaml package.json pnpm-workspace.yaml* ./

# Copy backend package files
COPY backend/package.json ./backend/

# Install production dependencies only
RUN pnpm install --filter backend --prod --frozen-lockfile

# Copy prisma schema for runtime
COPY backend/prisma ./backend/prisma/

# Generate Prisma client (needed at runtime)
WORKDIR /app/backend
RUN pnpm prisma:generate

# Copy built application from builder
COPY --from=builder /app/backend/dist ./dist

# Create non-root user
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nestjs -u 1001 -G nodejs

# Change ownership
RUN chown -R nestjs:nodejs /app

USER nestjs

EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

CMD ["node", "dist/main.js"]
