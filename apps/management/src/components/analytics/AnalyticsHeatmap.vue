<script setup lang="ts">
import { computed, ref } from 'vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export interface HeatmapCell {
  x: number
  y: number
  value: number
  label?: string
}

export interface HeatmapRow {
  label: string
}

export interface HeatmapColumn {
  label: string
}

const props = withDefaults(
  defineProps<{
    title?: string
    description?: string
    data: HeatmapCell[]
    rows?: HeatmapRow[]
    columns?: HeatmapColumn[]
    maxValue?: number
    cellSize?: number
    cellGap?: number
    showLabels?: boolean
    showTooltip?: boolean
  }>(),
  {
    cellSize: 20,
    cellGap: 3,
    showLabels: true,
    showTooltip: true,
  },
)

const hoveredCell = ref<HeatmapCell | null>(null)
const tooltipPosition = ref({ x: 0, y: 0 })

const maxVal = computed(() => {
  return props.maxValue || Math.max(...props.data.map((d) => d.value), 1)
})

const gridData = computed(() => {
  // Determine grid dimensions
  const maxX = Math.max(...props.data.map((d) => d.x)) + 1
  const maxY = Math.max(...props.data.map((d) => d.y)) + 1

  // Create 2D grid
  const grid: (HeatmapCell | null)[][] = Array(maxY)
    .fill(null)
    .map(() => Array(maxX).fill(null))

  // Fill grid with data
  props.data.forEach((cell) => {
    const row = grid[cell.y]
    if (row) {
      row[cell.x] = cell
    }
  })

  return { grid, maxX, maxY }
})

function getCellColor(value: number): string {
  const intensity = value / maxVal.value
  // Solarized: interpolate from the highlighted surface to the blue accent
  if (intensity === 0) {
    return 'var(--surface-highlight)'
  }
  // Use color-mix to blend from the highlighted surface to the blue accent
  const pct = Math.round(intensity * 70 + 10)
  return `color-mix(in oklch, var(--primary) ${pct}%, var(--surface-highlight))`
}

function getCellOpacity(value: number): number {
  const intensity = value / maxVal.value
  return 0.3 + intensity * 0.7
}

function getCellBorderStyle(value: number): string {
  const intensity = value / maxVal.value
  if (intensity <= 0.25) return 'dotted'
  if (intensity <= 0.5) return 'dashed'
  if (intensity <= 0.75) return 'solid'
  return 'double'
}

function getCellAriaLabel(cell: HeatmapCell): string {
  const row = props.rows?.[cell.y]?.label || `row ${cell.y + 1}`
  const column = props.columns?.[cell.x]?.label || `column ${cell.x + 1}`
  return `${cell.label ? `${cell.label}, ` : ''}${row}, ${column}: ${cell.value}`
}

function handleCellFocus(cell: HeatmapCell, event: FocusEvent) {
  if (!props.showTooltip) return
  const element = event.currentTarget as HTMLElement
  const rect = element.getBoundingClientRect()
  hoveredCell.value = cell
  tooltipPosition.value = {
    x: rect.left + rect.width / 2,
    y: rect.top,
  }
}

function handleMouseEnter(cell: HeatmapCell, event: MouseEvent) {
  if (!props.showTooltip) return
  hoveredCell.value = cell
  // Use event coordinates + known cell size to avoid getBoundingClientRect() forced reflow
  tooltipPosition.value = {
    x: event.clientX - props.cellSize / 2,
    y: event.clientY - props.cellSize / 2,
  }
}

function handleMouseLeave() {
  hoveredCell.value = null
}
</script>

<template>
  <Card
    class="border border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/60 bg-card shadow-float h-full gap-0 py-0 overflow-hidden rounded-none"
  >
    <CardHeader
      v-if="title"
      class="px-4 py-4 bg-[var(--surface)] dark:bg-[var(--surface-highlight)]/10 border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/50"
    >
      <CardTitle class="text-sm font-bold font-mono uppercase tracking-wide text-foreground">{{
        title
      }}</CardTitle>
      <CardDescription v-if="description" class="text-xs text-[var(--foreground-muted)] mt-1">
        {{ description }}
      </CardDescription>
    </CardHeader>
    <CardContent class="p-4">
      <div v-if="data.length === 0" class="text-center py-8 text-[var(--foreground-muted)] text-sm">
        {{ $t('common.noData') }}
      </div>
      <div v-else class="w-full flex justify-center overflow-x-auto">
        <div class="inline-flex flex-col" role="grid" :aria-label="title || description || 'Analytics heatmap'">
          <!-- Column labels -->
          <div v-if="showLabels && columns" class="flex mb-1">
            <div class="w-12 shrink-0" />
            <!-- Spacer for row labels -->
            <div
              v-for="(col, index) in columns"
              :key="index"
              class="flex items-center justify-center text-2xs text-[var(--foreground-muted)] font-data tabular-nums"
              :style="{
                width: cellSize + 'px',
                marginRight: (index < columns.length - 1 ? cellGap : 0) + 'px',
              }"
            >
              {{ col.label }}
            </div>
          </div>

          <!-- Grid -->
          <div class="flex flex-col">
            <div
              v-for="(row, rowIndex) in gridData.grid"
              :key="rowIndex"
              role="row"
              class="flex items-center"
              :style="{ marginBottom: rowIndex < gridData.maxY - 1 ? cellGap + 'px' : 0 }"
            >
              <!-- Row label -->
              <div
                v-if="showLabels && rows"
                class="shrink-0 w-12 text-right pr-2 text-2xs text-[var(--foreground-muted)] font-data tabular-nums"
              >
                {{ rows[rowIndex]?.label || '' }}
              </div>

              <!-- Cells -->
              <div class="flex" role="rowgroup">
                <div
                  v-for="(cell, colIndex) in row"
                  :key="colIndex"
                  :role="cell ? 'gridcell' : undefined"
                  :tabindex="cell ? 0 : -1"
                  :aria-label="cell ? getCellAriaLabel(cell) : undefined"
                  :aria-hidden="cell ? undefined : 'true'"
                  class="rounded-none transition-all duration-150 cursor-default hover:ring-1 hover:ring-[var(--accent-primary)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent-primary)]"
                  :style="{
                    width: cellSize + 'px',
                    height: cellSize + 'px',
                    marginRight: colIndex < row.length - 1 ? cellGap + 'px' : 0,
                    backgroundColor: cell ? getCellColor(cell.value) : 'var(--surface-highlight)',
                    opacity: cell ? getCellOpacity(cell.value) : 0.3,
                    border: cell ? '1px solid var(--border-control)' : '1px solid transparent',
                    borderStyle: cell ? getCellBorderStyle(cell.value) : 'solid',
                  }"
                  @mouseenter="cell && handleMouseEnter(cell, $event)"
                  @mouseleave="handleMouseLeave"
                  @focus="cell && handleCellFocus(cell, $event)"
                  @blur="handleMouseLeave"
                />
              </div>
            </div>
          </div>

          <!-- Legend -->
          <div
            class="flex items-center justify-end gap-2 mt-3 pt-3 border-t border-[var(--border-subtle)] dark:border-[var(--border-subtle)]"
          >
            <span class="text-2xs text-[var(--foreground-muted)]">{{
              $t('analytics.heatmap.less')
            }}</span>
            <div class="flex gap-0.5">
              <div
                v-for="i in 5"
                :key="i"
                class="rounded-none"
                :style="{
                  width: '12px',
                  height: '12px',
                  backgroundColor: getCellColor((i / 5) * maxVal),
                  opacity: getCellOpacity((i / 5) * maxVal),
                  border: '1px solid var(--border-control)',
                  borderStyle: getCellBorderStyle((i / 5) * maxVal),
                }"
                aria-hidden="true"
              />
            </div>
            <span class="text-2xs text-[var(--foreground-muted)]">{{
              $t('analytics.heatmap.more')
            }}</span>
          </div>
        </div>
      </div>

      <!-- Tooltip -->
      <Teleport to="body">
        <Transition
          enter-active-class="transition ease-out duration-150"
          enter-from-class="opacity-0 scale-95"
          enter-to-class="opacity-100 scale-100"
          leave-active-class="transition ease-in duration-100"
          leave-from-class="opacity-100 scale-100"
          leave-to-class="opacity-0 scale-95"
        >
          <div
            v-if="hoveredCell && showTooltip"
            class="fixed z-50 px-2 py-1 text-xs rounded-none border border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-card shadow-lg pointer-events-none"
            :style="{
              left: tooltipPosition.x + 'px',
              top: tooltipPosition.y - 8 + 'px',
              transform: 'translate(-50%, -100%)',
            }"
          >
            <span class="font-data tabular-nums text-foreground">{{ hoveredCell.value }}</span>
            <span v-if="hoveredCell.label" class="text-[var(--foreground-muted)] ml-1">
              {{ hoveredCell.label }}
            </span>
          </div>
        </Transition>
      </Teleport>
    </CardContent>
  </Card>
</template>
