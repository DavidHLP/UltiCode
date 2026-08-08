import { describe, expect, it } from 'vitest'
import { join } from 'path'
import zhCN from '../locales/zh-CN'
import { flattenObject, runI18nCheck } from '@/shared/i18n-completeness/src'

type TranslationObject = Record<string, unknown>

describe('i18n coverage', () => {
  it('zh-CN and en-US should have the same key count', async () => {
    const enUS = (await import('../locales/en-US')).default as unknown as TranslationObject
    const zhKeys = flattenObject(zhCN as unknown as TranslationObject)
    const enKeys = flattenObject(enUS)
    expect(enKeys.length).toBe(zhKeys.length)
  })

  it('should have no new missing i18n keys beyond known baseline', async () => {
    const enUS = (await import('../locales/en-US')).default as unknown as TranslationObject
    const report = await runI18nCheck({
      zhLocale: zhCN as unknown as TranslationObject,
      enLocale: enUS,
      srcDir: join(process.cwd(), 'src'),
      excludeFiles: ['check.ts'],
    })
    const missingKeys = report.codeCoverage.missingKeys

    // Known pre-existing missing keys (from inline <i18n> blocks and Phase 3 scope)
    // These will be fixed in Phase 3. Add new keys here only if they are pre-existing.
    const knownMissingPrefixes = [
      'testCases.', // inline <i18n> block in ViewCasesView/ViewCodeView/ViewDescriptionView
      'problems.bulkEdit.', // Phase 3: bulk edit UI text
      'problems.flagInfo.', // Phase 3: flag info display
      'problems.difficulty.', // Phase 3: difficulty enum display
      'common.delete', // Phase 3: delete confirmation
      'common.flag', // Phase 3: flag actions
      'common.reason', // Phase 3: reason prompts
      'audit.filters.', // Phase 3: audit filter labels
      'problemLists.status.', // Phase 3: problem list status
      'problemLists.form.', // Phase 3: problem list form
      'tags.toast.', // Phase 3: tag operation toasts
      'users.actions.', // Phase 3: user action descriptions
    ]

    const knownMissingKeys = new Set([
      'common.optional', // from TestCaseForm.vue inline <i18n>
      'common.deleteDescriptionWithName', // Phase 3
      'users.typeConfirmLabel', // Phase 3
      'forum.auditActions.', // trailing dot, likely a bug in source
    ])

    const newMissing = missingKeys.filter(
      ({ key }) =>
        !knownMissingPrefixes.some((prefix) => key.startsWith(prefix)) &&
        !knownMissingKeys.has(key),
    )

    // This test acts as a regression gate: if someone adds a new t() call with a key
    // that doesn't exist in the locale, this test will fail.
    // To fix: add the key to both zh-CN and en-US locale modules.
    if (newMissing.length > 0) {
      const summary = newMissing.map((m) => `  ${m.key}  (${m.file})`).join('\n')
      expect.fail(
        `Found ${newMissing.length} t() key(s) used in code but missing from zh-CN locale.\n` +
          `Add these keys to the appropriate locale module:\n${summary}`,
      )
    }
  })
})
