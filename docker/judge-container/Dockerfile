# UltiCode Judge Container - Secure Code Execution Sandbox
# This container provides isolated execution for user-submitted code

FROM node:20-alpine AS builder

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

# Create non-root user for running code
RUN addgroup -g 1001 judgeuser && \
    adduser -D -u 1001 -G judgeuser judgeuser

# Create app directory
WORKDIR /app

# Copy package files
COPY package*.json ./
COPY tsconfig.json ./

# Install dependencies (including devDependencies for TypeScript compilation)
RUN npm install && \
    npm cache clean --force

# Copy source code
COPY src ./src

# Build TypeScript
RUN npx tsc

# Remove devDependencies to reduce image size
RUN npm prune --production

# Production stage with minimal attack surface
FROM node:20-alpine

# Install dumb-init and remove unnecessary packages
RUN apk add --no-cache dumb-init && \
    rm -rf /var/cache/apk/* /tmp/*

# Create non-root user
RUN addgroup -g 1001 judgeuser && \
    adduser -D -u 1001 -G judgeuser judgeuser

# Create isolated working directory with proper permissions
RUN mkdir -p /workspace /tmp/judge && \
    chown -R judgeuser:judgeuser /workspace /tmp/judge

WORKDIR /app

# Copy built files from builder
COPY --from=builder --chown=judgeuser:judgeuser /app/dist ./dist
COPY --from=builder --chown=judgeuser:judgeuser /app/node_modules ./node_modules
COPY --from=builder --chown=judgeuser:judgeuser /app/package.json ./package.json

# Switch to non-root user
USER judgeuser

# Set environment for security
ENV NODE_ENV=production
ENV NODE_OPTIONS="--max-old-space-size=256"

# Expose the executor service port
EXPOSE 3000

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Start the executor service
CMD ["node", "dist/index.js"]
