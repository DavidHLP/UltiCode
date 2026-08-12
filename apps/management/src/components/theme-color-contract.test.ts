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
})
