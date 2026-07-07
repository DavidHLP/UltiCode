---
title: Maintenance Log
type: log
tags: [meta, type/log]
status: living
updated: 2026-07-07
sources: []
---

# Maintenance Log

> [!info] Format
> Append-only timeline of wiki evolution. Greppable: `grep "^## \[" log.md | tail -5`.
>
> Entry types: `bootstrap` · `ingest` · `query-filed` · `lint` · `fix` · `format` · `docs` · `refactor`.
>
> Format: `## [YYYY-MM-DD] <type> | <summary>`

---

## [2026-07-06] ingest | Sandbox rebuild runbook — masked-RE fingerprint, alpine base, seccomp cwd trap

New concept page [`concepts/sandbox-rebuild.md`](concepts/sandbox-rebuild.md)
distilling the 2026-07-05/06 session that traced every-judge-returns-Runtime-Error
to two stacked layers: a missing `ulticode-sandbox:latest` image, and a
`SANDBOX_SECCOMP_PROFILE` path that must resolve from the backend cwd
(`backend-spring/`, hence the `../` prefix). Documents the masked-RE fingerprint
(`memory=0.0MB` + `detail="Runtime error"`, caused by `sanitizeSandboxOutput`
dropping any line containing `docker`/`OCI runtime`), the alpine/musl vs
host-glibc c/cpp compile trap (host `g++ -static` also needs absent
`libstdc++-static`), and the HTTP-proxy + `--network=host` + aliyun apk
fallback. Command layer updated in lockstep: `CLAUDE.md` § Sandbox Harness
(diagnostic fingerprint, seccomp cwd trap, base-17 + container-compile recipe,
proxy fallback), `AGENTS.md` Development Startup (image-not-distributed callout
+ missing-image symptom), `scripts/dev/up.sh` (startup WARN if image absent),
`scripts/dev/init-env.sh` + `.env.example` (`SANDBOX_SECCOMP_PROFILE` →
`../docker/sandbox/...` + `SANDBOX_ENABLED` placeholder comment),
`docker/sandbox/harness/README.md` (debian→alpine, SandboxServiceImpl→
SandboxExecutorImpl, C/C++ skeleton→complete, host-vs-image build notes), and
`entities/sandbox.md` (alpine base correction + expanded Gotchas). Counts
53→54.

## [2026-07-05] refactor | Skeleton slim-down — MOC decoupling & dedup

Distilled the four skeleton files (avg 227 lines, 2.3× the wiki mean) back to
their contracts. Leaf pages (entity/concept/overview) were already inside the
length envelopes in SCHEMA §4 and are untouched.

**Decoupling (MOC)** — `SCHEMA.md §11 Graph view coloring` (89 lines of
Obsidian UI procedure) was the single biggest non-contract block in the schema.
Extracted to its own concept card
[`concepts/obsidian-graph-coloring.md`](concepts/obsidian-graph-coloring.md);
SCHEMA §11 now reads as a 5-line pointer + the load-bearing invariant
("the `type/<x>` tag is the single source of truth"). The concept page inherits
the full palette, setup recipe, verification, and Bases/Juggl/Excalibrain
alternatives. Zero external anchors referenced §11 (verified by grep), so no
breakage.

**Dedup (ruthless cut)** — `README.md` was repeating `SCHEMA.md`'s three-layers
/ three-operations / layout exposition verbatim. Compressed to a navigation MOC:
one-line essence, start-here table, layout sketch, and pointers. Stale bootstrap
counts (39→48, since superseded) replaced with pointers to `log.md` and the
manifest. README 82→49 lines.

**Skeleton log compression** — the five 2026-06-21 entries below were first-day
narrative (original bootstrap + the four polish passes that followed). Folded
into one canonical `bootstrap` entry; per-step detail is recoverable via
`git log --since=2026-06-21 --until=2026-06-22 -- wiki/`. log.md 364→~150 lines.

**Stale-reference fix** — `index.md`'s info callout cited "SCHEMA §9 for the
Obsidian callout (color-group) palette", but the palette section was removed
in an earlier revision (§9 is now Writing rules). Corrected to point at the
new concept card; counts row now defers to the manifest for the authoritative
number.

**Files touched (6)**: `wiki/SCHEMA.md` (§11 → pointer, frontmatter date),
`wiki/README.md` (full rewrite), `wiki/log.md` (this entry + compression),
`wiki/index.md` (stale-ref fix + register new concept),
`wiki/concepts/obsidian-graph-coloring.md` (new). Manifest regenerated.

## [2026-07-05] ingest | Dev Environment — code-review generalization follow-up

Code review of the 4 unpushed cold-start commits found the prior section's
"fix in `application.yml`" advice had become a prod-metrics hazard: the three
metric autoconfig excludes lived in base `application.yml` (loaded by every
profile), so prod silently lost all system/jvm/tomcat micrometer binders.
Commit `f175a17` moved the excludes + `management.metrics.enable.*` into
`application-dev.yml` (dev profile only — restating `ErrorMvcAutoConfiguration`
because Spring Boot profile list-replace drops the base list), so prod keeps
full metrics. Same commit aligned the pnpm toolchain (CI pnpm 10→11;
`console`/`management` `pnpm-workspace.yaml` unified on the `allowBuilds` map;
management 补 `msw:true` lost in the original migration) and corrected the
`ecosystem.config.cjs` `JAVA_TOOL_OPTIONS` comment. The dev-environment
overview's "Fix" subsection rewritten to point at `application-dev.yml`.

## [2026-07-05] ingest | Dev Environment — WSL2+Docker Desktop cold-start pitfalls

New H2 section in `wiki/overview/dev-environment-overview.md` for the three
traps hit during cold-start: (1) openssl missing on Fedora 44 WSL with
/usr/sbin off PATH, (2) MySQL 9.1 IPv6-only binding colliding with Docker
Desktop's broken IPv4→IPv6 port forwarding (fix: container fixed IPs + JDBC to
docker bridge IPs), (3) JDK 17.0.2 + WSL2 cgroup v2 NPE in actuator metric
binders (fix: autoconfig exclude three metric auto-configs). All three fixes
committed same session; the wiki section is the re-debug-suppression note.
Frontmatter `updated:` bumped to 2026-07-05; `sources:` adds `application.yml`.

## [2026-06-24] docs | wiki cross-links — back-link 9 orphan / weakly-linked pages

Full-link graph pass over `wiki/` (basename-resolved `[[...]]`; content inbound
`ci` excludes index/log/SCHEMA/templates/daily). Baseline was healthy — 99%
resolve, 4 "dead" links all template/SCHEMA placeholders — but 9 content pages
were under-linked: 2 orphans (`concepts/notification-dispatch-and-preferences`,
`concepts/sidebar-menu`, ci=0) and 7 weak (ci=1: `entities/{achievement, backup,
follow, i18n, monitoring, search, subscription}`). Root cause was mostly
missing back-links. Added one semantically-justified inbound link per page
across 9 files (page-tail `Cross-links` / `Related` / `Links out` only).
**Verified**: orphans 2→0, weak 7→0, all targets ci≥2; resolve-rate 98.9%.

## [2026-06-24] fix | sidebar-menu — cross-CR fixes (uncontrolled collapse, router-link coverage, CSS)

Closed all findings from three code-level CRs (claude/glm-5.2, codex/MiniMax-M3,
opencode/deepseek) on the sidebar-menu unification commits `a47423c4d..fc266ce10`.
`SidebarGroupCollapsible` + `SidebarParentItem` made purely uncontrolled;
`SidebarMenuSubItem` / `SidebarMenuItem` got `inheritAttrs:false` + dead
`attrClass` removed; CSS contract tightened (`color-mix` tinted backgrounds in
`@supports`, named `group/*` selectors); global `RouterLink` stub in
`src/__tests__/setup.ts` unlocked `as='link'` / Mode-A / `:to` coverage (41
specs, +14). `docs/architecture/` spec + 6 CRs retired (reviews recoverable via
`git show 66cd1be64`).

## [2026-06-24] ingest | Notification Dispatch & Preferences (ADR-004) concept page

Consolidated ADR-004 (previously Javadoc across 10+ notification files) into
one concept page: typed dispatcher, preference gate, 4 categories, idempotency
ledger, dual-path migration via `FeatureFlags.useNotificationIntent`. Filed
with the admin-broadcast preference-bypass fix (MARKETING/COMMUNICATION now
filtered; SECURITY/SYSTEM force-delivered).

## [2026-06-23] ingest | wiki manifest v1 — per-page git provenance

Added `wiki/.meta/manifest.json` — a derived, deterministic manifest binding
every wiki content page to its last git commit (`last_commit.sha` + author +
date + subject) plus `body_sha256`. Machine-traceable companion to the
hand-maintained frontmatter `updated:` date. New tooling
`scripts/dev/wiki-manifest.sh` generates and lints (`--check`):
`[stale-fm]` / `[unregistered]` / `[stale-entry]` / `[drift]` / `[head]`.

## [2026-06-22] ingest | Process Management concept page — PM2 vs Preview port mutex

New `wiki/concepts/process-management.md` codifying the two modes (PM2
long-running / Preview per-session) and the rule that you pick one, don't mix
on the same port. Three operational rules: which mode → which command;
`pnpm exec vite --strictPort` (not `pnpm dev`) for Preview; one
`scripts/dev/doctor.sh` as the read-only inspector. New file in sources:
`scripts/dev/doctor.sh` (4-port occupancy + PM2 health with `↺ ≥ 10` warning +
Docker container health; pure `bash` + `node`).

## [2026-06-21] bootstrap | Initial wiki scaffold + same-day polish (compressed)

First-day foundation. The original five entries (bootstrap → 9-module ingest →
callout formatting → Obsidian design polish → SCHEMA §11 procedure fix) are
folded here; per-step detail is recoverable via
`git log --since=2026-06-21 --until=2026-06-22 -- wiki/`.

**Scaffold (39→48 pages)** — created `README.md` / `SCHEMA.md` / `index.md` /
`log.md` + 7 overviews + 25 entities + 12 concepts + 4 templates, distilled
from `AGENTS.md`, `CLAUDE.md`, `.claude/rules/`, 26 backend modules, 35
migrations, `docker/sandbox/`, `scripts/dev/`, `ecosystem.config.cjs`, `shared/`.
Same day promoted 9 low-density modules to their own entity pages (admin,
search, i18n, monitoring, backup, achievement, follow, subscription; bookmark +
vote + edgeoperations merged into `interactions`).

**Obsidian vault wiring** — `type/<x>` tag added to every page's `tags:`
(convention codified in SCHEMA §5); Daily Notes + Templates core plugins
enabled; `templates/daily-note.md` + first `daily-notes/2026-06-21.md` created;
graph-view coloring keyed off `#type/<x>` (procedure now at
`concepts/obsidian-graph-coloring.md`).

**Callout palette** — semantic `[!xxx]` callouts applied across 44 content
pages (concept / entity / overview shapes). Each H2 section opens with the
appropriate callout (`[!question]` on "The problem", `[!success]` on "The
decision", `[!warning]` on "Gotchas", etc.).


## [2026-07-07] ingest | docs/ → wiki/ merge + reference rewrite

Folded the entire `docs/` tree into the wiki + repo, retired the directory
itself, and rewrote every `docs/` reference in the landing layer. 8 ADRs
landed as concept pages; the project's "the wiki has no `decisions/` /
`codemap/` / `theme/` dir" rule (SCHEMA §3) now covers what `docs/adr/`
held. Operational and binary assets moved to homes that match their
lifecycle, not their previous path.

**ADRs → `wiki/concepts/` (8 new pages, 1 enriched).** Per SCHEMA §3 an
ADR folds into `concepts/`. The 8 ADRs that had no wiki landing yet now
have one, in the established format (frontmatter + note callout + The
problem / The decision / Where it lives / Consequences / Rejected /
Related):

- ADR-0001 → [[concepts/submission-contest-port]] (port inversion, 4 contest
  mappers → 1 port; 4 R6 cross-module concerns concentrated)
- ADR-0004 → [[concepts/moderation-projection]] (10 pure-read methods +
  3 projections extracted from 760-LOC service)
- ADR-0006 → [[concepts/problem-detail-port]] (137-LOC write-side
  satellite orchestration extracted from `ProblemServiceImpl.updateProblem`)
- ADR-0007 → [[concepts/admin-user-stats-read-port]] (AdminReadModel user
  phase, primitive-typed return)
- ADR-0008 → [[concepts/admin-comment-read-port]] (AdminReadModel forum
  phase, typed-view cross-module reads)
- ADR-0009 → [[concepts/realtime-push-port-series]] (six per-consumer
  ports collapse the 230-LoC `RealtimeService` god service)
- ADR-0010 → [[concepts/contest-live-ranking-read-port]]
  (`RankingService.getLiveRanking` → narrow port, real seam)
- ADR-0011 → [[concepts/admin-projection-inversion]] (phased
  `AdminXxxProjection` rollout, Stage 1 ProblemList + Stage 2 Submission
  already landed; User + Solution + Forum + Contest + Comment next)

[[concepts/achievement-projection]] (ADR-0005) was already a concept page;
absorbed the full ADR-0005 source content (Consequences / Rejected /
Verification sections that the wiki entry didn't have), dropped the
`docs/adr/0005-...` source path. 8 new pages added to [[index]]; counts
57 → 65.

**`docs/PROJECT_STATUS_REPORT.md` → `wiki/.meta/` (historical archive).**
The 2026-06-19 progress snapshot is stale &mdash; every section it had
(tech stack, module list, dev-environment, current run state) is
superseded by the living wiki overviews. It lives on as a `wiki/.meta/`
artifact (per SCHEMA §3, `.meta/` is the right home for non-content
material), with the one `docs/` reference inside it rewired to a wiki
pointer.

**`docs/rtk-reference.md` → `wiki/.meta/rtk-reference.md` (reference
catalog).** RTK is a tool-layer artifact (command catalog), not a
content page; per SCHEMA §1 the wiki never duplicates
`AGENTS.md`/`CLAUDE.md` directives. The catalog moved to
`wiki/.meta/rtk-reference.md` (same home as the manifest); CLAUDE.md's
two pointers (table cell + body link) updated.

**`docs/screenshots/` → `assets/screenshots/` (binary assets).** README
images referenced as `docs/screenshots/<file>.png` &mdash; 17 PNGs + a
README. Binary assets are repo-level, not wiki-level, so they moved to
the new top-level `assets/screenshots/` directory. All 14 image
references + the two `[docs/screenshots/](...)` link references in
`README.md` updated; the regenerated `assets/screenshots/README.md`
points back at the new location.

**Reference rewrite (landing + code layers).**

- `AGENTS.md` &mdash; project map: `docs/` row replaced with
  `wiki/concepts/` (ADRs) + new `assets/` row. Security-docs paragraph
  reworded to point at `wiki/concepts/{csrf-mechanism,
  refresh-token-hash-only-storage, security-invariants}` instead of the
  never-existing `docs/SECURITY_REVIEW_2026-06-06.md`; the original
  review record is preserved in git history (`git log -- docs/`).
- `CLAUDE.md` &mdash; both RTK-reference pointers (table cell + body
  link) updated to `wiki/.meta/rtk-reference.md`.
- `CONTEXT.md` &mdash; the "see `docs/adr/`" line now points at
  `wiki/concepts/` with a wikilink to ADR-0001's landing page.
- `README.md` &mdash; 14 image references + 2 directory links to
  `docs/screenshots/` rewritten to `assets/screenshots/`; the project
  tree entry for `docs/` replaced with `assets/`; the documentation
  navigation table's "决策记录 (ADR)" + "架构师 / 规划者" rows now point
  at `wiki/` + `wiki/concepts/`; the dangling
  `docs/adr/0005-rolling-deploy-rollback.md` (pre-existing dead ref;
  current ADR-0005 is achievement-projection, not rolling-deploy)
  replaced with a pointer to the dev-environment overview. The other
  dangling refs to never-existed `docs/CONTRIBUTING.md` / `docs/RUNBOOK.md`
  / `docs/CODEMAPS/` / `docs/ENV.md` / `docs/theme/README.md` / `docs/SECURITY_REVIEW_2026-06-06.md`
  are pre-existing tech debt and outside this merge's scope.
- `wiki/concepts/sidebar-menu.md` &mdash; the two `docs/architecture/`
  references in the note callout + trade-offs reworded to
  `git show 66cd1be64` (the dir was already gone before this merge).
- Code layer: 6 Java Javadoc refs (`backend-spring/.../notification/{email,
  channel, intent, ledger}/...`) that pointed at
  `docs/adr/ADR-004-notification-intents.md` (a renamed/folded ADR)
  rewired to `wiki/concepts/notification-dispatch-and-preferences.md`.
  3 SQL migration comments (`init-db/migrations/V20260613{100000,110000,120000}`)
  that pointed at the never-existed `docs/adr/ADR-003-queue-outbox-fencing.md`
  + an old "ADR-005 §2.X" pre-merge dead ref rewired to
  `wiki/concepts/exactly-once-judging.md` (the concept page that
  already absorbed the outbox + generation fence + lease pattern).
  1 shell script (`scripts/adr-005/create-milestone-issues.sh`) had a
  dangling `docs/adr/ADR-005-rolling-deploy-playbook.md`; the line is
  now a pointer to the archived 10-milestone plan in
  `wiki/.meta/PROJECT_STATUS_REPORT.md`.

**Manifest regenerated.** `scripts/dev/wiki-manifest.sh` re-emitted
`wiki/.meta/manifest.json` with the 8 new pages; frontmatter `updated:`
on the touched pages bumped to 2026-07-07.

**Out of scope (left as-is).** Daily notes (`wiki/daily-notes/`) and
the `wiki/log.md` line 128 entry both still mention `docs/architecture/`
in historical context (what was retired on 2026-06-24). They are
historical records &mdash; rewriting them would falsify the journal. The
framework-level `.agents/skills/*` and `.claude/skills/*` files that say
"ADRs go in `docs/adr/`" are generic matt-pocock-skill templates; not
project-specific. The pre-existing dangling `docs/CONTRIBUTING.md` /
`docs/RUNBOOK.md` / `docs/CODEMAPS/` / `docs/ENV.md` / `docs/theme/`
references in `README.md` are pre-existing tech debt (the files never
existed) and unrelated to this merge.
