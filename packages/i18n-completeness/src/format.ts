import type { CheckReport } from './types'

/**
 * Render a {@link CheckReport} as human-readable console output. The caller
 * decides the process exit code from `report.summary.passed`.
 */
export function formatReport(report: CheckReport): void {
  const { localeConsistency, codeCoverage, summary } = report

  console.log('\n=== i18n Translation Completeness Check ===\n')

  // Locale consistency
  console.log(`zh-CN keys: ${localeConsistency.zhKeysCount}`)
  console.log(`en-US keys: ${localeConsistency.enKeysCount}`)

  if (localeConsistency.missingInEn.length > 0) {
    console.log(`\n❌ Missing in en-US (${localeConsistency.missingInEn.length} keys):`)
    localeConsistency.missingInEn.slice(0, 20).forEach((key) => console.log(`   - ${key}`))
    if (localeConsistency.missingInEn.length > 20) {
      console.log(`   ... and ${localeConsistency.missingInEn.length - 20} more`)
    }
  }

  if (localeConsistency.missingInZh.length > 0) {
    console.log(`\n❌ Missing in zh-CN (${localeConsistency.missingInZh.length} keys):`)
    localeConsistency.missingInZh.slice(0, 20).forEach((key) => console.log(`   - ${key}`))
    if (localeConsistency.missingInZh.length > 20) {
      console.log(`   ... and ${localeConsistency.missingInZh.length - 20} more`)
    }
  }

  if (localeConsistency.isComplete) {
    console.log('\n✅ Locale-to-locale consistency: PASS')
  }

  // Code coverage
  console.log(`\n--- Code-to-Locale Coverage ---`)
  console.log(`Static t() keys found in source: ${codeCoverage.totalStaticKeys}`)

  if (codeCoverage.missingKeys.length > 0) {
    console.log(
      `\n❌ Keys used in code but missing in zh-CN locale (${codeCoverage.missingKeys.length}):`,
    )
    codeCoverage.missingKeys
      .slice(0, 30)
      .forEach(({ key, file }) => console.log(`   - ${key}  (${file})`))
    if (codeCoverage.missingKeys.length > 30) {
      console.log(`   ... and ${codeCoverage.missingKeys.length - 30} more`)
    }
  } else {
    console.log('\n✅ Code-to-locale coverage: PASS')
  }

  // Dynamic prefixes (informational)
  if (codeCoverage.dynamicPrefixes.length > 0) {
    console.log(
      `\nℹ️  Dynamic key prefixes (${codeCoverage.dynamicPrefixes.length}) — verify sub-keys exist:`,
    )
    codeCoverage.dynamicPrefixes.forEach(({ prefix, file }) =>
      console.log(`   - ${prefix}.*  (${file})`),
    )
  }

  // Summary
  console.log('\n' + '='.repeat(45))
  if (summary.totalIssues === 0) {
    console.log('✅ All i18n checks passed!')
  } else {
    console.log(`❌ ${summary.totalIssues} issue(s) found. Fix before merging.`)
  }
  console.log('')
}
