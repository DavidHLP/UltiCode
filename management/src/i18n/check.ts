/**
 * i18n Translation Completeness Checker — management adapter.
 *
 * Delegates locale consistency + code-to-locale coverage to the shared engine
 * (../../shared/i18n-completeness). This adapter only wires the management
 * locale trees and source root, then renders the report.
 *
 * Usage:
 *   pnpm check:i18n          # human-readable output
 *   pnpm check:i18n --json   # JSON report for CI
 */

import { join } from 'path'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'
import { formatReport, runI18nCheck } from '../../../shared/i18n-completeness'

async function main() {
  const isJson = process.argv.includes('--json')

  const report = await runI18nCheck({
    zhLocale: zhCN as unknown as Record<string, unknown>,
    enLocale: enUS as unknown as Record<string, unknown>,
    srcDir: join(process.cwd(), 'src'),
    excludeFiles: ['check.ts'],
  })

  if (isJson) {
    console.log(JSON.stringify(report, null, 2))
    process.exit(report.summary.passed ? 0 : 1)
    return
  }

  formatReport(report)
  process.exit(report.summary.passed ? 0 : 1)
}

main()
