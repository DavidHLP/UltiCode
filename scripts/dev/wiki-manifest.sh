#!/usr/bin/env bash
# scripts/dev/wiki-manifest.sh — generate / lint the wiki page-provenance manifest
#
# wiki/.meta/manifest.json records, for every wiki content page, the last git
# commit that modified it (SHA + author + date + subject) plus a body hash.
# It is the machine-traceable companion to the hand-maintained frontmatter
# `updated:` date — answering "which commit last touched this page?" for
# audits, stale detection, and CI. See wiki/SCHEMA.md §12.
#
# Usage:
#   scripts/dev/wiki-manifest.sh             generate (default)
#   scripts/dev/wiki-manifest.sh --check     lint; exit 1 on any finding
#   scripts/dev/wiki-manifest.sh -h|--help
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

usage() {
  cat <<'EOF'
Usage: scripts/dev/wiki-manifest.sh [--check] [-h]

  (no arg) | generate   Regenerate wiki/.meta/manifest.json from git history.
  --check | check       Lint: report unregistered pages, deleted-page entries,
                        content/commit drift, stale frontmatter dates, and a
                        moved HEAD anchor. Exits 1 on any finding.
  -h | --help           Show this help.
EOF
}

MODE="generate"
case "${1:-}" in
  "" | generate) MODE="generate" ;;
  --check | check) MODE="check" ;;
  -h | --help) usage; exit 0 ;;
  *) echo "unknown argument: $1" >&2; usage; exit 2 ;;
esac

# Core logic in node — frontmatter parse + JSON emit + git history are far
# cleaner than bash. stdin heredoc is quoted ('NODE') so bash never expands
# ${} / backticks; node sees the script verbatim.
exec node - "$ROOT_DIR" "$MODE" <<'NODE'
const fs = require("fs");
const path = require("path");
const cp = require("child_process");
const crypto = require("crypto");

const ROOT = process.argv[2] || process.cwd();
const MODE = process.argv[3] || "generate";
const WIKI = path.join(ROOT, "wiki");
const MANIFEST = path.join(WIKI, ".meta", "manifest.json");

function git(args) {
  try {
    return cp.execSync("git " + args, {
      cwd: ROOT, encoding: "utf8", stdio: ["pipe", "pipe", "pipe"],
    }).trim();
  } catch (e) { return ""; }
}

// Collect wiki/*.md, skipping dot-directories (.obsidian/.claude/.claudian/.meta).
function walk(d) {
  const out = [];
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) { if (!e.name.startsWith(".")) out.push(...walk(p)); }
    else if (e.isFile() && e.name.endsWith(".md")) out.push(p);
  }
  return out;
}
const rel = (p) => path.relative(ROOT, p);

function parseFM(content) {
  const m = content.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!m) return { fm: "", body: content };
  return { fm: m[1], body: content.slice(m[0].length).replace(/^\r?\n/, "") };
}
function fmGet(fm, k) {
  const mm = fm.match(new RegExp("^" + k + ":\\s*(.*)$", "m"));
  return mm ? mm[1].trim().replace(/^['"]|['"]$/g, "") : "";
}

function buildAll() {
  const files = walk(WIKI).sort();
  const byType = {};
  const pages = files.map((abs) => {
    const content = fs.readFileSync(abs, "utf8");
    const { fm, body } = parseFM(content);
    const rpath = rel(abs);
    // %x1f (unit separator) delimits fields; subjects never contain it.
    const raw = git("log -1 --format='%H%x1f%h%x1f%cI%x1f%an%x1f%s' -- '" + rpath.replace(/'/g, "'\\''") + "'");
    let last = { sha: null, short: null, committed_at: null, author: null, subject: null };
    if (raw) {
      const p = raw.split("\x1f");
      if (p.length >= 5) last = { sha: p[0], short: p[1], committed_at: p[2], author: p[3], subject: p[4] };
    }
    const type = fmGet(fm, "type") || "(none)";
    byType[type] = (byType[type] || 0) + 1;
    return {
      path: rpath,
      type,
      title: fmGet(fm, "title"),
      status: fmGet(fm, "status"),
      frontmatter_updated: fmGet(fm, "updated"),
      last_commit: last,
      body_sha256: crypto.createHash("sha256").update(body.replace(/\r\n/g, "\n"), "utf8").digest("hex"),
    };
  });
  const byTypeSorted = {};
  Object.keys(byType).sort().forEach((k) => { byTypeSorted[k] = byType[k]; });
  return {
    "$schema": "wiki-manifest-v1",
    generated_with_head: git("rev-parse HEAD"),
    stats: { pages: pages.length, by_type: byTypeSorted },
    pages,
  };
}

if (MODE === "generate") {
  fs.mkdirSync(path.dirname(MANIFEST), { recursive: true });
  fs.writeFileSync(MANIFEST, JSON.stringify(buildAll(), null, 2) + "\n");
  const m = JSON.parse(fs.readFileSync(MANIFEST, "utf8"));
  console.log("manifest generated: " + MANIFEST);
  console.log("  pages: " + m.stats.pages + "   head: " + (m.generated_with_head || "").slice(0, 10));
  console.log("  by_type: " + JSON.stringify(m.stats.by_type));
  process.exit(0);
}

// MODE === "check"
if (!fs.existsSync(MANIFEST)) {
  console.error("no manifest at " + MANIFEST + "; run: scripts/dev/wiki-manifest.sh");
  process.exit(1);
}
const existing = JSON.parse(fs.readFileSync(MANIFEST, "utf8"));
const fresh = buildAll();
const existingByPath = new Map((existing.pages || []).map((p) => [p.path, p]));
const freshByPath = new Map(fresh.pages.map((p) => [p.path, p]));
const day = (s) => (s ? String(s).slice(0, 10) : "");

let findings = 0;
const lines = [];

for (const p of fresh.pages) {
  if (!existingByPath.has(p.path)) {
    lines.push("  [unregistered] page not in manifest: " + p.path); findings++;
  }
}
for (const p of existing.pages || []) {
  if (!freshByPath.has(p.path)) {
    lines.push("  [stale-entry] manifest references missing file: " + p.path); findings++;
  }
}
for (const p of fresh.pages) {
  const ex = existingByPath.get(p.path);
  if (!ex) continue;
  if (ex.body_sha256 !== p.body_sha256) {
    lines.push("  [drift] content changed since manifest recorded: " + p.path); findings++;
  }
  if (ex.last_commit && p.last_commit && ex.last_commit.sha !== p.last_commit.sha) {
    lines.push("  [drift] last_commit advanced: " + p.path +
      " (" + (ex.last_commit.short || "?") + " -> " + (p.last_commit.short || "?") + ")"); findings++;
  }
}
for (const p of fresh.pages) {
  const fu = day(p.frontmatter_updated);
  const lc = day(p.last_commit && p.last_commit.committed_at);
  if (fu && lc && lc > fu) {
    lines.push("  [stale-fm] frontmatter_updated=" + fu + " behind last_commit=" + lc + ": " + p.path); findings++;
  }
}
if (existing.generated_with_head && existing.generated_with_head !== fresh.generated_with_head) {
  lines.push("  [head] generated_with_head advanced: " +
    (existing.generated_with_head || "").slice(0, 10) + " -> " + (fresh.generated_with_head || "").slice(0, 10) +
    " (regenerate manifest)"); findings++;
}

if (findings === 0) {
  console.log("manifest OK — " + fresh.stats.pages + " pages in sync with " + (fresh.generated_with_head || "").slice(0, 10));
  process.exit(0);
}
console.error("manifest lint found " + findings + " issue(s):");
console.error(lines.join("\n"));
process.exit(1);
NODE
