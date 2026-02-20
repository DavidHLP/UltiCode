# Judge Service Architecture

## Overview

The UltiCode Judge Service provides secure, isolated execution of user-submitted code through Docker containerization. This architecture replaces the insecure Node.js `vm` module with production-grade sandboxing.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Backend Application                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     JudgeService                                 │   │
│  │  - Entry point for code execution requests                      │   │
│  │  - Routes to Docker or legacy vm based on feature flag         │   │
│  └────────────────────┬────────────────────────────────────────────┘   │
│                       │                                                  │
│                       ▼                                                  │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │              DockerOrchestratorService                           │   │
│  │  - Manages container lifecycle                                   │   │
│  │  - Handles communication with containers                         │   │
│  │  - Monitors execution metrics                                    │   │
│  └────────────────────┬────────────────────────────────────────────┘   │
│                       │                                                  │
│                       ▼                                                  │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                ContainerPoolService                              │   │
│  │  - Manages pool of reusable containers                           │   │
│  │  - Handles container acquisition/release                         │   │
│  │  - Prunes idle containers                                        │   │
│  └────────────────────┬────────────────────────────────────────────┘   │
│                       │                                                  │
│                       ▼                                                  │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   Docker Daemon                                  │   │
│  │  - Container creation/destruction                                │   │
│  │  - Resource allocation (cgroups)                                 │   │
│  │  - Security profiles (seccomp, AppArmor)                         │   │
│  └────────────────────┬────────────────────────────────────────────┘   │
│                       │                                                  │
└───────────────────────┼──────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Docker Host                                      │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │              Judge Container (Isolated)                          │   │
│  │  ┌─────────────────────────────────────────────────────────┐    │   │
│  │  │         Express HTTP Server (Port 3000)                  │    │   │
│  │  ├─────────────────────────────────────────────────────────┤    │   │
│  │  │              Code Executor                               │    │   │
│  │  │  - vm module (safe inside container)                     │    │   │
│  │  │  - TypeScript transpilation                              │    │   │
│  │  │  - Test case execution                                   │    │   │
│  │  └─────────────────────────────────────────────────────────┘    │   │
│  │                                                                   │   │
│  │  Security Layers:                                                 │   │
│  │  - No network (network_mode: none)                                │   │
│  │  - Read-only root filesystem                                      │   │
│  │  - Seccomp syscall filtering                                     │   │
│  │  - cgroups resource limits                                       │   │
│  │  - Non-root user (UID 1001)                                      │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Component Descriptions

### JudgeService

**Location:** `backend/src/submission/judge.service.ts`

**Responsibilities:**
- Primary interface for code execution
- Feature flag based routing (Docker vs legacy vm)
- Input validation and normalization
- Result aggregation and formatting

**Key Methods:**
- `judge(language, code, testCases)` - Main execution method
- `judgeWithDocker(...)` - Docker-based execution
- `legacyJudge(...)` - Legacy vm-based execution (deprecated)

### DockerOrchestratorService

**Location:** `backend/src/submission/services/docker-orchestrator.service.ts`

**Responsibilities:**
- Container lifecycle management
- Communication with containers via HTTP
- Execution metrics collection
- Error handling and recovery

**Key Methods:**
- `executeInSandbox(...)` - Execute code in container
- `checkImageExists()` - Verify container image availability
- `getPoolStats()` - Get container pool statistics

### ContainerPoolService

**Location:** `backend/src/submission/services/container-pool.service.ts`

**Responsibilities:**
- Maintain pool of reusable containers
- Container acquisition and release
- Idle container pruning
- Resource management

**Key Methods:**
- `acquire()` - Get available container or create new
- `release(container)` - Return container to pool
- `pruneIdleContainers()` - Remove unused containers
- `getStats()` - Get pool statistics

### Judge Container

**Location:** `backend/docker/judge-container/`

**Components:**
- `Dockerfile` - Container image definition
- `src/executor.ts` - HTTP server and code executor
- `src/seccomp-profile.json` - System call restrictions

**Security Features:**
- Non-root user execution
- Read-only filesystem
- Network isolation
- Resource limits
- Seccomp filtering

## Execution Flow

### 1. Request Processing

```
User Request
    ↓
SubmissionController
    ↓
JudgeService.judge()
    ↓
Feature Flag Check
    ↓
┌───────────────────────┐
│ JUDGE_CONTAINER_ENABLED? │
└───────────────────────┘
         ↓
    ┌────┴────┐
    │         │
   Yes       No
    │         │
    ↓         ↓
Docker    Legacy vm
Orchestrator (insecure)
    │
    ↓
ContainerPool.acquire()
```

### 2. Container Execution

```
DockerOrchestrator.executeInSandbox()
    ↓
ContainerPool.acquire()
    ↓
┌────────────────────────┐
│ Container Available?   │
└────────────────────────┘
         ↓
    ┌────┴────┐
    │         │
   Yes       No
    │         │
    ↓         ↓
Return    Create Container
Container    ↓
    │     Start Container
    │         ↓
    └────→ Return Container
         ↓
HTTP POST /execute
    ↓
Container executes code
    ↓
Return result
    ↓
ContainerPool.release()
```

### 3. Container Pool Management

```
┌─────────────────────────────────────────┐
│           Container Pool                 │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐       │
│  │ C1  │ │ C2  │ │ C3  │ │ C4  │ ...    │
│  │free │ │busy │ │busy │ │free │       │
│  └─────┘ └─────┘ └─────┘ └─────┘       │
│                                         │
│  Min Pool Size: 5                       │
│  Max Containers: 10                     │
│  Prune Interval: 60s                    │
└─────────────────────────────────────────┘

Request arrives → Acquire free container
                 ↓
              No free containers?
                 ↓
            Create new container
                 ↓
          At max capacity?
                 ↓
          Reject/Queue request
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JUDGE_CONTAINER_ENABLED` | Enable Docker sandbox | `false` |
| `JUDGE_CONTAINER_IMAGE` | Container image name | `ulticode-judge:latest` |
| `JUDGE_CONTAINER_POOL_SIZE` | Minimum pool size | `5` |
| `JUDGE_CONTAINER_MAX_CONTAINERS` | Maximum containers | `10` |
| `JUDGE_DEFAULT_TIME_LIMIT` | Execution timeout (ms) | `2000` |
| `JUDGE_DEFAULT_MEMORY_LIMIT` | Memory limit (MB) | `256` |
| `DOCKER_SOCKET_PATH` | Docker socket | `/var/run/docker.sock` |

### Container Resources

| Resource | Limit | Description |
|----------|-------|-------------|
| Memory | 256MB | Maximum heap usage |
| CPU | 0.5 cores | 50% of one CPU core |
| Processes | 50 | Maximum process count |
| Timeout | 2s | Execution time limit |

## Monitoring

### Metrics Collected

- **Container Creation Rate**: Containers created per minute
- **Pool Utilization**: Percentage of containers in use
- **Execution Time**: Average code execution duration
- **Memory Usage**: Peak memory per execution
- **Error Rate**: Failed executions percentage

### Health Checks

- Container health endpoint: `GET /health`
- Pool status: `DockerOrchestrator.getPoolStats()`
- Image availability: `DockerOrchestrator.checkImageExists()`

## Migration Strategy

### Phase 1: Implementation (Week 1-2)
- Implement Docker solution
- Keep legacy vm code
- Feature flag defaults to `false`

### Phase 2: Testing (Week 3-4)
- Test with `JUDGE_CONTAINER_ENABLED=false`
- Verify backward compatibility
- Run integration tests

### Phase 3: Staging (Week 5)
- Enable in staging: `JUDGE_CONTAINER_ENABLED=true`
- Monitor metrics
- Run security tests

### Phase 4: Production (Week 6)
- Enable in production
- Monitor for 2 weeks
- Remove legacy code after stable operation

### Rollback Plan

If issues occur:
1. Set `JUDGE_CONTAINER_ENABLED=false`
2. Restart backend service
3. System falls back to legacy vm module
4. Investigate and fix Docker issues

## Security Considerations

### Defense in Depth

1. **Container Isolation**: Linux namespaces
2. **Resource Limits**: cgroups enforcement
3. **Syscall Filtering**: Seccomp profiles
4. **Filesystem Protection**: Read-only root
5. **Network Isolation**: Disabled networking
6. **Privilege Dropping**: Non-root user

### Threat Model

| Threat | Mitigation |
|--------|------------|
| Sandbox escape | Multiple isolation layers |
| Resource exhaustion | cgroups limits |
| Network attacks | Network disabled |
| Filesystem access | Read-only root |
| Privilege escalation | Non-root user |
| Syscall abuse | Seccomp filtering |

## Performance

### Optimization Strategies

1. **Container Pooling**: Reuse containers to avoid startup overhead
2. **Lazy Pruning**: Keep idle containers for potential reuse
3. **Connection Reuse**: Maintain Docker daemon connection
4. **Parallel Execution**: Future support for concurrent test cases

### Benchmarks

| Operation | Target | Actual |
|-----------|--------|--------|
| Cold start | <3s | TBD |
| Warm execution | <500ms | TBD |
| Pool acquire | <50ms | TBD |
| Memory per container | <256MB | ~100MB |

## Future Enhancements

1. **Async Execution**: Full async judge API
2. **Parallel Test Cases**: Run multiple tests concurrently
3. **Language Support**: Python, Java, Go, etc.
4. **Custom Seccomp Profiles**: Per-language restrictions
5. **Distributed Execution**: Multi-host container scheduling
6. **Result Caching**: Cache identical submissions
