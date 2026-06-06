#!/usr/bin/env node
// ---------------------------------------------------------------------------
// verify-theme-sync.mjs — guard against FOUC bootstrap drift
//
// `console/public/theme-bootstrap.js` and `management/public/theme-bootstrap.js`
// are intentionally duplicated copies of the logic in
// `shared/theme/src/applyThemeToDOM.ts` (they MUST be plain JS so Vite can
// serve them as <script src="…"> in the HTML head, eliminating the white
// flash on dark-mode reloads).
//
// The duplication is required; the drift is not. This script verifies the
// two copies stay byte-identical AND contain the expected key behavior
// markers — so a future edit to one will fail CI until both are updated.
//
// Usage:
//   node scripts/verify-theme-sync.mjs
//   pnpm verify:theme-sync
// ---------------------------------------------------------------------------

import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __filename = fileURLToPath(import.meta.url)
const ROOT = dirname(dirname(__filename))

const BOOTSTRAP_FILES = [
  join(ROOT, 'console/public/theme-bootstrap.js'),
  join(ROOT, 'management/public/theme-bootstrap.js'),
]

// Behavior markers that MUST appear in every bootstrap. If the TS source
// `applyThemeToDOM.ts` changes its contract, update both the TS file and
// these markers in lock-step.
const REQUIRED_MARKERS = [
  // persisted-mode key (mirrors THEME_STORAGE_KEY in shared/theme/src/ThemeMode.ts)
  "'ulticode-theme'",
  // matchMedia media query
  "'(prefers-color-scheme: dark)'",
  // explicit mode branches
  "mode === 'dark'",
  "mode === 'light'",
  // final DOM action
  "html.classList.add('dark')",
  "html.classList.remove('dark')",
]

function sha256(buf) {
  return createHash('sha256').update(buf).digest('hex')
}

function fail(msg) {
  console.error(`✘ ${msg}`)
  process.exit(1)
}

function ok(msg) {
  console.log(`✓ ${msg}`)
}

const contents = BOOTSTRAP_FILES.map((p) => {
  try {
    return { path: p, body: readFileSync(p, 'utf8') }
  } catch (e) {
    fail(`Cannot read ${p}: ${e.message}`)
  }
})

// 1. Both files must exist.
if (contents.length !== BOOTSTRAP_FILES.length) fail('Missing bootstrap file')

// 2. Both files must be byte-identical.
const [a, b] = contents
const aHash = sha256(a.body)
const bHash = sha256(b.body)
if (aHash !== bHash) {
  console.error('  console/public/theme-bootstrap.js sha256 =', aHash)
  console.error('  management/public/theme-bootstrap.js sha256 =', bHash)
  fail('Bootstrap files diverged — copy the change between the two files in lock-step')
}
ok(`Bootstrap files are byte-identical (sha256: ${aHash.slice(0, 12)}…)`)

// 3. All required behavior markers must be present.
for (const marker of REQUIRED_MARKERS) {
  if (!a.body.includes(marker)) {
    fail(`Missing required marker ${JSON.stringify(marker)} — update marker list AND/OR restore the marker in both bootstrap files`)
  }
}
ok(`All ${REQUIRED_MARKERS.length} required behavior markers present`)

console.log('Theme bootstrap sync OK.')
