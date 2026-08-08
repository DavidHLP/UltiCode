import { afterAll, describe, expect, it } from 'vitest'
import { mkdir, mkdtemp, rm, writeFile } from 'fs/promises'
import { tmpdir } from 'os'
import { join } from 'path'
import {
  buildKeySet,
  checkLocaleConsistency,
  extractDynamicKeyPrefixes,
  extractStaticKeys,
  flattenObject,
  keyExists,
  runI18nCheck,
} from './index'

describe('flattenObject / buildKeySet / keyExists', () => {
  it('flattens nested objects to dot notation and treats arrays as leaves', () => {
    const locale = { a: { b: { c: 1 }, list: ['x', 'y'] }, leaf: 'v' }
    expect(flattenObject(locale).sort()).toEqual(['a.b.c', 'a.list', 'leaf'])
  })

  it('keyExists walks the dotted path and rejects missing segments', () => {
    const locale = { a: { b: 1 } }
    expect(keyExists(locale, 'a.b')).toBe(true)
    expect(keyExists(locale, 'a.c')).toBe(false)
    expect(keyExists(locale, 'a.b.c')).toBe(false)
  })

  it('buildKeySet dedupes keys', () => {
    expect(buildKeySet({ a: { b: 1, c: 2 } }).size).toBe(2)
  })
})

describe('extractStaticKeys', () => {
  it('collects dotted keys across quote styles and skips non-i18n paths', () => {
    const content = [
      `t('a.b')`,
      `t("c.d")`,
      't(`e.f`)',
      `t('/api/x')`,
      `t('@/import')`,
      `t('no-dot')`,
      `t('has space here')`,
      `t('emit:event')`,
      `this.$t('g.h')`,
    ].join('\n')
    expect(extractStaticKeys(content).sort()).toEqual(['a.b', 'c.d', 'e.f', 'g.h'])
  })

  it('skips template-literal interpolation', () => {
    expect(extractStaticKeys('t(`prefix.${x}`)')).toEqual([])
  })
})

describe('extractDynamicKeyPrefixes', () => {
  it('returns the dotted prefix of a template-literal t() call', () => {
    expect(extractDynamicKeyPrefixes('t(`moderation.status.${status}`)')).toEqual([
      'moderation.status',
    ])
  })

  it('ignores api-style template paths', () => {
    expect(extractDynamicKeyPrefixes('t(`/api/${id}`)')).toEqual([])
  })
})

describe('checkLocaleConsistency', () => {
  it('reports keys missing on either side', () => {
    const zh = { shared: 1, onlyZh: 2 }
    const en = { shared: 1, onlyEn: 2 }
    const result = checkLocaleConsistency(zh, en)
    expect(result.isComplete).toBe(false)
    expect(result.missingInEn).toEqual(['onlyZh'])
    expect(result.missingInZh).toEqual(['onlyEn'])
  })

  it('passes when both sides match', () => {
    const result = checkLocaleConsistency({ a: { b: 1 } }, { a: { b: 1 } })
    expect(result.isComplete).toBe(true)
    expect(result.zhKeysCount).toBe(1)
  })
})

describe('runI18nCheck', () => {
  const fixture = { root: '' }

  afterAll(async () => {
    if (fixture.root) {
      await rm(fixture.root, { recursive: true, force: true })
    }
  })

  it('detects a static t() key missing from the locale', async () => {
    fixture.root = await mkdtemp(join(tmpdir(), 'i18n-cov-'))
    await mkdir(join(fixture.root, 'views'), { recursive: true })
    await writeFile(
      join(fixture.root, 'views', 'View.vue'),
      `t('missing.key')\nt('present.key')\n`,
    )

    const report = await runI18nCheck({
      zhLocale: { present: { key: 'ok' } },
      enLocale: { present: { key: 'ok' } },
      srcDir: fixture.root,
    })

    expect(report.codeCoverage.totalStaticKeys).toBe(2)
    expect(report.codeCoverage.missingKeys.map((m) => m.key)).toEqual(['missing.key'])
    expect(report.summary.passed).toBe(false)
    expect(report.summary.totalIssues).toBe(1)
  })
})
