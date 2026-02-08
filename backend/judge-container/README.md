# UltiCode Judge Container

Secure Docker container for executing user-submitted JavaScript/TypeScript code.

## Security Features

- **Process Isolation**: Linux namespaces provide complete process isolation
- **Resource Limits**: cgroups enforce CPU (0.5 cores), memory (256MB), and process (50) limits
- **Filesystem Isolation**: Read-only root filesystem with temporary workspace only
- **Network Isolation**: `network_mode: none` prevents all network access
- **Seccomp Profile**: Restricts system calls to only those necessary for execution
- **Non-root User**: Runs as UID 1001 with minimal privileges
- **Ephemeral**: Containers are destroyed after each execution

## Building

```bash
cd backend/judge-container
docker build -t ulticode-judge:latest .
```

## Running Standalone (for testing)

```bash
docker run --rm \
  --network none \
  --read-only \
  --memory=256m \
  --cpus=0.5 \
  --pids-limit=50 \
  --security-opt seccomp=src/seccomp-profile.json \
  --tmpfs /tmp:rw,noexec,nosuid,size=100m \
  --tmpfs /workspace:rw,noexec,nosuid,size=100m \
  -p 3000:3000 \
  ulticode-judge:latest
```

## API Endpoints

### POST /execute

Execute code in the isolated environment.

**Request Body:**
```json
{
  "code": "function add(a, b) { return a + b; }",
  "language": "javascript",
  "testCases": [
    {
      "id": "1",
      "inputs": [
        { "name": "a", "value": "1" },
        { "name": "b", "value": "2" }
      ],
      "output": "3"
    }
  ],
  "timeLimit": 2000
}
```

**Response:**
```json
{
  "success": true,
  "result": {
    "verdict": "Accepted",
    "runtime": 5,
    "memory": 45.2,
    "cases": [
      {
        "status": "Accepted",
        "time": 5,
        "memory": 45.2,
        "output": "3",
        "expectedOutput": "3",
        "inputs": [...]
      }
    ]
  }
}
```

### GET /health

Health check endpoint.

**Response:**
```json
{
  "status": "healthy",
  "timestamp": 1234567890
}
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         Docker Host                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Judge Container (isolated)              │   │
│  │                                                       │   │
│  │  ┌───────────────────────────────────────────────┐  │   │
│  │  │        Express Server (Port 3000)              │  │   │
│  │  ├───────────────────────────────────────────────┤  │   │
│  │  │        Code Executor (vm module)               │  │   │
│  │  │        - Safe inside container isolation      │  │   │
│  │  └───────────────────────────────────────────────┘  │   │
│  │                                                       │   │
│  │  Security Layers:                                     │   │
│  │  - No network (network_mode: none)                   │   │
│  │  - Read-only filesystem                               │   │
│  │  - Seccomp syscall filtering                         │   │
│  │  - cgroups resource limits                           │   │
│  │  - Non-root user (UID 1001)                          │   │
│  │                                                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

## Supported Languages

- JavaScript (ES2020)
- TypeScript (transpiled to JavaScript)

## Resource Limits

- **Memory**: 256MB
- **CPU**: 0.5 cores
- **Processes**: 50 max
- **Execution Time**: 2 seconds (configurable)
- **Filesystem**: Read-only (except /tmp and /workspace)

## Development

For local development:

```bash
npm install
npm run build
npm start
```

## Security Notes

This container is designed to run untrusted user code. The vm module inside the container is safe because:

1. The container provides the primary security boundary
2. Network access is completely disabled
3. The filesystem is read-only
4. System calls are filtered via seccomp
5. Resources are strictly limited via cgroups

Even if code escapes the vm module, it cannot escape the container.
