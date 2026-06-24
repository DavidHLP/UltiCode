---
title: Arthas Diagnostics
type: concept
tags: [ops, runtime, diagnostics, type/concept]
status: living
updated: 2026-06-21
sources:
  - tools/arthas-boot.jar
  - infrastructure/arthas/arthas.properties
  - scripts/start-arthas.sh
  - scripts/arthas-cli.sh
  - CLAUDE.md
aliases: [Arthas 诊断]
---

# Arthas Diagnostics

## The problem
A running Spring Boot JVM needs inspection (hot method, slow trace, loaded class)
without redeploy or restart. And the LLM wants to drive Arthas directly over MCP.

## The decision
- `tools/arthas-boot.jar` (4.2.2) attaches to the target JVM; the MCP endpoint at
  `http://localhost:8563/mcp` lets Claude Code call `dashboard`/`thread`/`watch`/
  `trace`/`ognl`/`jad` directly.
- **STATELESS protocol is project-pinned.** 4.2.2's default STREAMABLE demands an
  `mcp-session-id` header the Claude Code MCP client doesn't send → blocking
  commands time out at 30s ("Session ID required"). Pinned in
  `infrastructure/arthas/arthas.properties`; `scripts/start-arthas.sh` syncs it to
  the actually-effective `~/.arthas/lib/4.2.2/arthas/arthas.properties`. Changing
  protocol requires `pm2 restart ulticode-9001` to re-attach.
- **Three-way mutual exclusion** (PM2 / Claude hook / CLI) via PID file
  (`PID\nLAUNCHER`) + port `:8563`; whichever is up wins, others skip.
- **Blocking commands** (`dashboard`/`trace`/`watch`/`monitor`/`tt`) must carry
  `-n N` (N ≤ 5) and a concurrent trigger to fire the target method, or the 30s
  MCP timeout hits.

## Degrade path (when MCP blocks)
1. `pm2 logs ulticode-9001 --nostream --lines 200` (most perf/exception issues).
2. Same with `--raw` (unformatted stacks, match against `jad` output).
3. `scripts/arthas-cli.sh` → interactive telnet (no MCP 30s limit).
4. `./mvnw -Dtest='*IT' test` — integration test as a control.
5. `ctx_execute` running Java reflection/grep in a subprocess (no MCP timeout).

## Where it lives
- `tools/arthas-boot.jar`, `infrastructure/arthas/arthas.properties`,
  `scripts/start-arthas.sh`, `scripts/arthas-cli.sh`, `.claude/.arthas/`.

## Trade-offs
- STATELESS loses streaming session state — acceptable; the client doesn't need it.
- Don't downgrade to 4.1.9 — STATELESS works on 4.2.2.

## Related
[[overview/dev-environment-overview]] · [[overview/architecture-overview]] · [[entities/monitoring]]
