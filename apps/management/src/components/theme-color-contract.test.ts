import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const SOURCE_FILES = [
  'src/components/analytics/AnalyticsMetricCard.vue',
  'src/components/dashboard/DashboardTimeline.vue',
  'src/views/tags/TagsListView.vue',
]

const readSource = (file: string) => readFileSync(resolve(process.cwd(), file), 'utf8')

describe('management semantic color contracts', () => {
  it('does not append alpha bytes to CSS variables or external tag colors', () => {
    for (const file of SOURCE_FILES) {
      const source = readSource(file)
      expect(source).not.toMatch(/\+\s*['"][0-9a-f]{2}['"]/i)
      expect(source).not.toMatch(/\$\{\s*(?:getIconColor|tag\.color)[^}]*\}[0-9a-f]{2}/i)
      expect(source).toContain('color-mix(in srgb')
    }
  })

  it('keeps switches on official paired foregrounds', () => {
    const settings = readSource('src/views/settings/components/GeneralSettings.vue')
    const switchSource = readSource(
      '../../packages/design-system/src/components/ui/switch/Switch.vue',
    )
    expect(settings).toContain('@ulticode/design-system/ui/switch')
    expect(switchSource).toContain('data-checked:bg-primary')
    expect(switchSource).toContain('dark:data-checked:bg-primary-foreground')
  })

  it('uses a defined semantic primary fallback for tag surfaces', () => {
    const source = readSource('src/views/tags/TagsListView.vue')
    expect(source).toContain(
      "color-mix(in srgb, ${tag.color || 'var(--primary)'} 12%, transparent)",
    )
  })

  it('keeps shared shell controls on the public surface contract', () => {
    const navigation = readSource(
      '../../packages/design-system/src/components/ui/navigation-menu/NavigationMenuLink.vue',
    )
    const popover = readSource(
      '../../packages/design-system/src/components/ui/popover/PopoverContent.vue',
    )
    const input = readSource('../../packages/design-system/src/components/ui/input/Input.vue')

    expect(navigation).toContain('bg-muted')
    expect(navigation).toContain('focus-visible:ring-')
    expect(popover).toContain('rounded-lg')
    expect(popover).toContain('bg-popover')
    expect(input).toContain('border-input')
    expect(input).toContain('rounded-lg')
  })

  it('uses semantic locale markers instead of emoji flags', () => {
    const switcher = readSource('src/components/LanguageSwitcher.vue')
    const localeTypes = readSource('src/i18n/types.ts')

    expect(switcher).toContain("localeConfig.code.split('-')[0].toUpperCase()")
    expect(switcher).toContain('bg-muted')
    expect(switcher).toContain('border-input')
    expect(switcher).toContain('@ulticode/design-system/ui/')
    expect(switcher).not.toContain('localeConfig.flag')
    expect(switcher).not.toContain('bg-[var(--primary)]')
    expect(localeTypes).not.toContain('flag:')
  })
})
