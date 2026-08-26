import type { EChartsOption } from 'echarts'

export interface TopUserChartDatum {
  username: string
  loginCount: number
}

export interface TopUserChartColors {
  accent: string
  accentMuted: string
  axis: string
  border: string
  card: string
  foreground: string
}

export function createTopUsersChartOption(
  users: TopUserChartDatum[],
  colors: TopUserChartColors,
  formatLogins: (count: number) => string,
): EChartsOption {
  return {
    animationDuration: 450,
    grid: {
      top: 8,
      right: 28,
      bottom: 18,
      left: 92,
    },
    tooltip: {
      trigger: 'axis',
      renderMode: 'richText',
      axisPointer: {
        type: 'shadow',
        shadowStyle: {
          color: colors.accentMuted,
          opacity: 0.12,
        },
      },
      backgroundColor: colors.card,
      borderColor: colors.border,
      borderWidth: 1,
      padding: [8, 10],
      textStyle: {
        color: colors.foreground,
        fontFamily: '"JetBrains Mono", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", monospace',
        fontSize: 11,
      },
      formatter: (params) => {
        const item = Array.isArray(params) ? params[0] : params
        const value = Number(item?.value ?? 0)
        return `${item?.name ?? ''}<br/><strong>${formatLogins(value)}</strong>`
      },
    },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: {
        lineStyle: { color: colors.border },
      },
      axisTick: { show: false },
      axisLabel: {
        color: colors.axis,
        fontFamily: '"JetBrains Mono", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", monospace',
        fontSize: 9,
      },
      splitLine: {
        lineStyle: {
          color: colors.border,
          opacity: 0.35,
          type: 'dashed',
        },
      },
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: users.map((user) => user.username),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: colors.axis,
        fontFamily: '"JetBrains Mono", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", monospace',
        fontSize: 9,
        overflow: 'truncate',
        width: 78,
      },
    },
    series: [
      {
        type: 'bar',
        data: users.map((user) => user.loginCount),
        barMaxWidth: 12,
        itemStyle: {
          color: colors.accent,
          borderColor: colors.accentMuted,
          borderWidth: 1,
          opacity: 0.78,
        },
        emphasis: {
          itemStyle: {
            color: colors.accentMuted,
            opacity: 1,
          },
        },
        label: {
          show: true,
          position: 'right',
          color: colors.foreground,
          fontFamily: '"JetBrains Mono", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", monospace',
          fontSize: 9,
        },
      },
    ],
  }
}
