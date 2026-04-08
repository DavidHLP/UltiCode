#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# UltiCode Development Environment Setup
# ============================================================
# Run this script after cloning to prepare your environment.
# Prerequisites: Docker, Node.js 18+, Java 17, Python 3.10+
# ============================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"

# ── 1. Check prerequisites ──────────────────────────────────
info "Checking prerequisites..."

command -v docker >/dev/null 2>&1 || error "Docker is required. Install: https://docs.docker.com/get-docker/"
command -v node >/dev/null 2>&1 || error "Node.js 18+ is required. Install: https://nodejs.org/"
command -v java >/dev/null 2>&1 || error "Java 17 is required. Install: https://adoptium.net/"
command -v python3 >/dev/null 2>&1 || error "Python 3.10+ is required."

java_version=$(java -version 2>&1 | head -1 | grep -oP '"\K[^"]*' | cut -d. -f1)
java_update=$(java -version 2>&1 | head -1 | grep -oP '"\K[^"]*' | cut -d. -f2)
if [ "$java_version" -lt 17 ]; then
  error "Java 17+ required, found Java $java_version"
fi
if [ "$java_version" -eq 17 ] && [ "$java_update" -lt 6 ]; then
  warn "Java 17.0.$java_update has a cgroup v2 bug. Recommend 17.0.6+ from https://adoptium.net/"
fi

info "All prerequisites met."

# ── 2. Create .env from example ─────────────────────────────
if [ ! -f .env ]; then
  cp .env.example .env
  # Auto-generate a secure JWT_SECRET
  JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
  if command -v sed >/dev/null 2>&1; then
    sed -i "s|^JWT_SECRET=.*|JWT_SECRET=\"$JWT_SECRET\"|" .env
  fi
  warn "Created .env from .env.example with auto-generated JWT_SECRET."
else
  info ".env already exists, skipping."
fi

# ── 3. Install PM2 globally ─────────────────────────────────
if ! command -v pm2 >/dev/null 2>&1; then
  info "Installing PM2 globally..."
  npm install -g pm2
else
  info "PM2 already installed."
fi

# ── 4. Install frontend dependencies ────────────────────────
info "Installing console dependencies (this may take a minute)..."
cd "$PROJECT_ROOT/console" && npm install --legacy-peer-deps
info "Installing management dependencies..."
cd "$PROJECT_ROOT/management" && npm install --legacy-peer-deps

# ── 5. Setup db-manager venv ────────────────────────────────
info "Setting up db-manager Python environment..."
cd "$PROJECT_ROOT/db-manager"
if [ ! -d .venv ]; then
  python3 -m venv .venv
fi
source .venv/bin/activate
pip install -e ".[dev]" --quiet 2>/dev/null || pip install -e . --quiet
deactivate 2>/dev/null || true

# ── 5b. Install Flyway (if not present) ─────────────────────
if ! command -v flyway >/dev/null 2>&1; then
  info "Installing Flyway CLI..."
  FLYWAY_VERSION="10.20.1"
  FLYWAY_DIR="$PROJECT_ROOT/db-manager/flyway"
  if [ ! -f "$FLYWAY_DIR/flyway" ]; then
    mkdir -p "$FLYWAY_DIR"
    FLYWAY_URL=""
    if [ "$(uname -s)" = "Linux" ]; then
      FLYWAY_URL="https://repo1.maven.org/maven2/org/flywaydb/flyway-commandline/${FLYWAY_VERSION}/flyway-commandline-${FLYWAY_VERSION}-linux-x64.tar.gz"
    elif [ "$(uname -s)" = "Darwin" ]; then
      FLYWAY_URL="https://repo1.maven.org/maven2/org/flywaydb/flyway-commandline/${FLYWAY_VERSION}/flyway-commandline-${FLYWAY_VERSION}-macosx-x64.tar.gz"
    fi
    if [ -n "$FLYWAY_URL" ]; then
      curl -fSL "$FLYWAY_URL" | tar xz -C "$FLYWAY_DIR" --strip-components=1 || error "Flyway download failed. Install manually from https://flyway.net/"
    else
      error "Unsupported OS. Install Flyway manually from https://flyway.net/"
    fi
    chmod +x "$FLYWAY_DIR/flyway" 2>/dev/null
  fi
  info "Flyway installed to $FLYWAY_DIR/flyway"
fi

# ── 6. Start Docker services ────────────────────────────────
info "Starting Docker services (MySQL, Redis, Nacos)..."
cd "$PROJECT_ROOT"
docker compose up -d

# ── 7. Wait for MySQL to be healthy ─────────────────────────
info "Waiting for MySQL to be ready..."
max_attempts=30
attempt=0
while [ $attempt -lt $max_attempts ]; do
  if docker exec ulticode-mysql mysqladmin ping -h localhost -uroot -proot --silent 2>/dev/null; then
    break
  fi
  attempt=$((attempt + 1))
  sleep 2
done
if [ $attempt -eq $max_attempts ]; then
  error "MySQL did not become healthy in time."
fi
info "MySQL is ready."

# ── 8. Run database migrations ──────────────────────────────
info "Running database migrations..."
cd "$PROJECT_ROOT/db-manager"
source .venv/bin/activate
python -m db_manager.cli migrate
deactivate 2>/dev/null || true

# ── 9. Summary ──────────────────────────────────────────────
echo ""
echo -e "${GREEN}============================================================${NC}"
echo -e "${GREEN}  UltiCode setup complete!${NC}"
echo -e "${GREEN}============================================================${NC}"
echo ""
echo "Start all services:"
echo "  pm2 start ecosystem.config.cjs"
echo ""
echo "Or start individually:"
echo "  pm2 start ecosystem.config.cjs --only ulticode-9001  # Backend"
echo "  pm2 start ecosystem.config.cjs --only ulticode-9002  # Console"
echo "  pm2 start ecosystem.config.cjs --only ulticode-9003  # Management"
echo ""
echo "Access:"
echo "  Console:     http://localhost:9002"
echo "  Management:  http://localhost:9003"
echo "  Backend API: http://localhost:9001"
echo "  Nacos:       http://localhost:28848/nacos"
echo ""
