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

  it('keeps status-mark controls on the adaptive control foreground', () => {
    const settings = readSource('src/views/settings/components/GeneralSettings.vue')
    const switchSource = readSource('src/components/ui/switch/Switch.vue')

    expect(settings).toContain('data-[state=checked]:bg-status-warning-mark')
    expect(switchSource).toContain('data-[state=checked]:bg-primary-control-foreground')
    expect(switchSource).not.toContain('dark:data-[state=checked]:bg-primary-foreground')
  })

  it('uses a defined semantic primary fallback for tag surfaces', () => {
    const source = readSource('src/views/tags/TagsListView.vue')
    expect(source).toContain("color-mix(in srgb, ${tag.color || 'var(--primary)'} 12%, transparent)")
  })

  it('keeps shared shell controls on the public surface contract', () => {
    const navigation = readSource('src/components/ui/navigation-menu/NavigationMenuLink.vue')
    const popover = readSource('src/components/ui/popover/PopoverContent.vue')
    const input = readSource('src/components/ui/input/Input.vue')

    expect(navigation).toContain('bg-surface-highlight')
    expect(navigation).toContain('focus-visible:ring-2')
    expect(popover).toContain('rounded-lg')
    expect(popover).toContain('shadow-float')
    expect(input).toContain('bg-surface-sunken')
    expect(input).toContain('rounded-md')
  })

  it('uses semantic locale markers instead of emoji flags', () => {
    const switcher = readSource('src/components/LanguageSwitcher.vue')
    const localeTypes = readSource('src/i18n/types.ts')

    expect(switcher).toContain("localeConfig.code.split('-')[0].toUpperCase()")
    expect(switcher).toContain('bg-surface-sunken')
    expect(switcher).toContain('border-border-control')
    expect(switcher).toContain('shadow-float')
    expect(switcher).not.toContain('localeConfig.flag')
    expect(switcher).not.toContain('bg-[var(--accent-primary)]')
    expect(localeTypes).not.toContain('flag:')
  })
})
