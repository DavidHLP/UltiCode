import { describe, expect, it } from 'vitest'
import { UI_PALETTE } from '@ulticode/design-system'
import { createTopUsersChartOption, type TopUserChartColors } from './topUsersChart'

const colors: TopUserChartColors = {
  accent: UI_PALETTE.blue,
  accentMuted: UI_PALETTE.cyan,
  axis: UI_PALETTE.muted,
  border: UI_PALETTE.muted,
  card: UI_PALETTE.darkCard,
  foreground: UI_PALETTE.border,
}

describe('createTopUsersChartOption', () => {
  it('maps users and theme colors into an ECharts bar option', () => {
    const option = createTopUsersChartOption(
      [
        { username: 'alice', loginCount: 12 },
        { username: 'bob', loginCount: 7 },
      ],
      colors,
      (count) => `${count} logins`,
    )

    expect(option.yAxis).toMatchObject({
      type: 'category',
      data: ['alice', 'bob'],
      axisLabel: { color: colors.axis },
    })
    expect(option.xAxis).toMatchObject({
      type: 'value',
      minInterval: 1,
    })
    expect(option.series).toEqual([
      expect.objectContaining({
        type: 'bar',
        data: [12, 7],
        label: expect.objectContaining({ position: 'right' }),
        itemStyle: expect.objectContaining({ color: colors.accent }),
      }),
    ])
    expect(option.tooltip).toMatchObject({
      renderMode: 'richText',
      backgroundColor: colors.card,
      borderColor: colors.border,
      textStyle: { color: colors.foreground },
    })
  })
})
