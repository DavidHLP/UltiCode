#!/usr/bin/env node
// ---------------------------------------------------------------------------
// sync-theme-bootstrap.mjs — regenerate the two public FOUC bootstrap copies
// from the canonical source packages/theme/bootstrap.js.
//
// The copies at apps/console/public/theme-bootstrap.js and
// apps/management/public/theme-bootstrap.js are GENERATED artifacts — edit the
// source, then run this script (or `pnpm sync:theme-bootstrap`).
// `verify:theme-sync` guards the equality in CI.
//
// Usage: node scripts/sync-theme-bootstrap.mjs
// ---------------------------------------------------------------------------
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __filename = fileURLToPath(import.meta.url)
const ROOT = dirname(dirname(__filename))

const SOURCE = join(ROOT, 'packages/theme/bootstrap.js')
const TARGETS = [
  join(ROOT, 'apps/console/public/theme-bootstrap.js'),
  join(ROOT, 'apps/management/public/theme-bootstrap.js'),
]

let src
try {
  src = readFileSync(SOURCE, 'utf8')
} catch (e) {
  console.error(`✘ Cannot read canonical source ${SOURCE}: ${e.message}`)
  console.error('  Did you create packages/theme/bootstrap.js?')
  process.exit(1)
}

for (const target of TARGETS) {
  try {
    mkdirSync(dirname(target), { recursive: true })
    writeFileSync(target, src)
    console.log(`✓ synced ${target.replace(ROOT + '/', '')}`)
  } catch (e) {
    console.error(`✘ Cannot write ${target}: ${e.message}`)
    process.exit(1)
  }
}
console.log('Theme bootstrap sync complete (2 copies regenerated from source).')
