#!/usr/bin/env node
// ---------------------------------------------------------------------------
// verify-typography-tokens.mjs — guard against raw typography overrides
//
// Enforces the rule from docs/SHARED_TYPOGRAPHY_DESIGN.md:
//   "Raw `font-size`, `font-family`, and arbitrary `text-[...]` values
//    should be restricted to shared token files and rare one-off layout
//    fixes."
//
// Both apps (console, management) MUST source their typography from
// packages/theme/src/typography.css — either via the shared utility classes
// (`uc-type-*`, `font-mono`, `terminal-label`, ...) or via the
// `--uc-*` / `--text-*` CSS variables. A new raw override is treated
// as a regression and fails this check.
//
// The script is strict: a deliberate, documented exception is the
// ONLY way to keep a raw override. To whitelist a file, add it to
// the `ALLOWED_PATH_PATTERNS` list below with a comment explaining
// why the exception exists.
//
// Usage:
//   node scripts/verify-typography-tokens.mjs
//   pnpm verify:typography-tokens
// ---------------------------------------------------------------------------

import { readdirSync, readFileSync, statSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join, relative, sep } from 'node:path'

const __filename = fileURLToPath(import.meta.url)
const ROOT = dirname(dirname(__filename))

// Apps to scan. Add new frontends here when they adopt the shared
// typography contract.
const SCAN_ROOTS = ['console/src', 'management/src']

// File globs the guardrail inspects. .vue/.ts/.tsx/.js/.mjs/.css are
// the only file types that can carry typography declarations in the
// project; .json/.lock are excluded by the type filter below.
const FILE_EXTENSIONS = new Set(['.vue', '.ts', '.tsx', '.js', '.mjs', '.cjs', '.css'])

// Paths that are EXPLICITLY allowed to declare raw typography. Each
// entry must come with a comment explaining why it cannot consume the
// shared tokens. The shared foundation is the natural home for any new
// raw declaration; do NOT add app files here without a written reason.
const ALLOWED_PATH_PATTERNS = [
  // The shared source of truth owns the canonical declarations.
  'packages/theme/src/typography.css',
  // packages/design-system/style.css imports the shared typography
  // module and is the one place outside packages/theme that can
  // re-anchor the Tailwind aliases. The script's regex matches
  // `--text-sm` only when it is preceded by a raw rem value, so the
  // shared aliases are safe.
  'packages/design-system/style.css',
  // The app-level style.css files are allowed to import the shared
  // foundation and define app-wide layout adjustments (e.g. terminal
  // table padding, header button height). They MUST NOT introduce new
  // raw font-size/font-family values — if you need a new typography
  // token, add it to packages/theme/src/typography.css instead.
  'console/src/style.css',
  'management/src/style.css',
  // The chart / markdown asset CSS files in console ship with the
  // chart engine; they declare typography for SVG/ECharts output that
  // cannot consume the Tailwind utility classes. They are allowed
  // because they describe the chart rendering pipeline, not the app
  // chrome. Any app-level override should go through shared tokens.
  'console/src/assets/charts.css',
  'console/src/assets/markdown.css',
]

// Patterns that count as raw typography declarations. The intent is
// to catch the common Tailwind arbitrary-value escapes AND any
// raw `font-family:` / `font-size:` literal in CSS or style="...".
// All matches are reported by file:line with the offending text.
//
// The shared tokens (`var(--uc-*)`, `var(--font-*)`, `var(--text-*)`,
// `var(--leading-*)`, `var(--tracking-*)`) are the *intended* way to
// declare typography in CSS, so the regex requires the literal value
// to NOT start with `var(` — declarations like
// `font-family: var(--uc-font-code);` are the shared contract, not
// a regression.
const PATTERNS = [
  // Tailwind arbitrary font-size: text-[Npx], text-[Nrem], text-[Nem]
  { name: 'arbitrary-font-size', regex: /(^|[\s"'(])text-\[\s*\d+(\.\d+)?\s*(px|rem|em)\s*\]/g },
  // Tailwind arbitrary font-family: font-["stack", "with", "quotes"]
  { name: 'arbitrary-font-family', regex: /(^|[\s"'(])font-\[\s*[A-Za-z"][^\]]{2,}\]/g },
  // Tailwind arbitrary letter-spacing (positive or negative)
  //   tracking-[Nem], tracking-[Npx], tracking-[-Nem]
  { name: 'arbitrary-tracking', regex: /(^|[\s"'(])tracking-\[\s*-?\d+(\.\d+)?\s*(em|px|rem)\s*\]/g },
  // Tailwind arbitrary line-height
  { name: 'arbitrary-leading', regex: /(^|[\s"'(])leading-\[\s*\d+(\.\d+)?\s*(px|rem|em|%)\s*\]/g },
  // Negative letter-spacing is deprecated per docs/SHARED_TYPOGRAPHY_DESIGN.md
  // §5.5. The shared foundation maps `--tracking-tight` (the legacy
  // alias) to 0, so any explicit `letter-spacing: -N.NNem` in app code
  // is a regression. Catches both `letter-spacing:` in CSS / <style>
  // blocks and Tailwind's `tracking-[-Nem]` escape (already covered by
  // arbitrary-tracking). CJK text in particular must not use negative
  // tracking; see design doc §15.
  { name: 'negative-letter-spacing', regex: /(^|[\s;{])letter-spacing\s*:\s*(?!var\()\s*-\s*\d+(\.\d+)?\s*(em|px|rem)\b/gm },
  // Raw CSS font-size in .css files / <style> blocks. Catches literals
  // like `font-size: 12px` but ignores `font-size: var(--uc-text-xs);`.
  // The `(?!var\b)\S` is a backtracking-safe pair: the negative
  // lookahead rejects the `v` of `var(...)`, and the `\S` anchors the
  // match to a position that must be the start of the value. Without
  // the trailing `\S`, the regex engine backtracks the surrounding
  // `\s*` and the negative lookahead becomes a no-op.
  { name: 'raw-font-size', regex: /(^|[\s;{])font-size\s*:\s*(?!var\b)\S[^;{}\n]*?(\d+(\.\d+)?\s*(px|rem|em|pt|%))/gm },
  // Raw CSS font-family in .css files / <style> blocks. Catches literals
  // like `font-family: "JetBrains Mono", ...` but ignores
  // `font-family: var(--uc-font-code);`. Same backtracking-safe
  // construction as raw-font-size.
  { name: 'raw-font-family', regex: /(^|[\s;{])font-family\s*:\s*(?!var\b)\S[^;{}\n]+;/gm },
  // Raw CSS font-weight in .css files / <style> blocks. Catches numeric
  // weights like `font-weight: 750` but ignores both
  // `font-weight: var(--uc-font-weight-bold)` and named keywords
  // (normal/bold/bolder/lighter/inherit/initial/unset). The shared
  // foundation exposes the four documented weights (regular/medium/
  // semibold/bold) — anything else in app code is a regression against
  // docs/SHARED_TYPOGRAPHY_DESIGN.md §5.4. Numeric values like 750 have
  // appeared historically (console auth links) and drifted the design
  // system; this rule funnels them back through the shared tokens.
  { name: 'raw-font-weight', regex: /(^|[\s;{])font-weight\s*:\s*(?!var\b|normal\b|bold\b|bolder\b|lighter\b|inherit\b|initial\b|unset\b)\s*\d{1,3}\b/gm },
]

function isAllowedPath(relPath) {
  return ALLOWED_PATH_PATTERNS.some((p) => relPath === p || relPath.startsWith(p + sep))
}

function listFiles(root) {
  const out = []
  const stack = [root]
  while (stack.length > 0) {
    const dir = stack.pop()
    let entries
    try {
      entries = readdirSync(dir, { withFileTypes: true })
    } catch {
      continue
    }
    for (const entry of entries) {
      const full = join(dir, entry.name)
      if (entry.isDirectory()) {
        if (
          entry.name === 'node_modules' ||
          entry.name === 'dist' ||
          entry.name === 'coverage' ||
          entry.name.startsWith('.')
        ) {
          continue
        }
        stack.push(full)
      } else if (entry.isFile()) {
        const dot = entry.name.lastIndexOf('.')
        if (dot === -1) continue
        const ext = entry.name.slice(dot)
        if (FILE_EXTENSIONS.has(ext)) out.push(full)
      }
    }
  }
  return out
}

function fail(msg) {
  console.error(`✘ ${msg}`)
  process.exit(1)
}

function ok(msg) {
  console.log(`✓ ${msg}`)
}

const violations = []

for (const scanRoot of SCAN_ROOTS) {
  const absRoot = join(ROOT, scanRoot)
  let exists = true
  try {
    statSync(absRoot)
  } catch {
    exists = false
  }
  if (!exists) {
    fail(`Cannot find scan root: ${scanRoot}`)
  }

  const files = listFiles(absRoot)
  for (const file of files) {
    const rel = relative(ROOT, file)
    if (isAllowedPath(rel)) continue

    let body
    try {
      body = readFileSync(file, 'utf8')
    } catch (e) {
      fail(`Cannot read ${rel}: ${e.message}`)
    }

    // Skip generated dist files just in case
    if (rel.includes(`${sep}dist${sep}`) || rel.endsWith(`${sep}dist`)) continue

    for (const { name, regex } of PATTERNS) {
      // Reset regex state on each file
      regex.lastIndex = 0
      let match
      while ((match = regex.exec(body)) !== null) {
        // Compute the 1-indexed line number of the match
        const upto = body.slice(0, match.index)
        const line = upto.split('\n').length
        violations.push({ file: rel, line, name, text: match[0].trim() })
      }
    }
  }
}

if (violations.length > 0) {
  console.error('Raw typography overrides detected. Apps must consume shared tokens.')
  console.error('Allowed declaration sites are listed in scripts/verify-typography-tokens.mjs')
  console.error('(ALLOWED_PATH_PATTERNS). Add new typography tokens to')
  console.error('packages/theme/src/typography.css instead of redeclaring them locally.')
  console.error('')
  // Group by file for compact output
  const byFile = new Map()
  for (const v of violations) {
    if (!byFile.has(v.file)) byFile.set(v.file, [])
    byFile.get(v.file).push(v)
  }
  for (const [file, list] of byFile) {
    console.error(`  ${file}`)
    for (const v of list) {
      console.error(`    L${v.line.toString().padStart(4, ' ')}  [${v.name}]  ${v.text}`)
    }
  }
  console.error('')
  console.error(`Total: ${violations.length} raw override(s) across ${byFile.size} file(s).`)
  process.exit(1)
}

ok(`No raw typography overrides in ${SCAN_ROOTS.join(', ')}.`)
ok('All app typography flows through shared tokens.')
