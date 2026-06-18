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
  // Solarized: interpolate from base2 (silver-100) to blue accent
  if (intensity === 0) {
    return 'var(--silver-100)'
  }
  // Use color-mix to blend from silver-100 to accent-electric
  const pct = Math.round(intensity * 70 + 10)
  return `color-mix(in oklch, var(--accent-electric) ${pct}%, var(--silver-100))`
}

function getCellOpacity(value: number): number {
  const intensity = value / maxVal.value
  return 0.3 + intensity * 0.7
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
    class="border border-[var(--silver-200)] dark:border-[var(--silver-300)]/60 bg-card shadow-float h-full gap-0 py-0 overflow-hidden rounded-none"
  >
    <CardHeader
      v-if="title"
      class="px-4 py-4 bg-[var(--silver-50)] dark:bg-[var(--silver-100)]/10 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]/50"
    >
      <CardTitle class="text-sm font-bold font-mono uppercase tracking-wide text-foreground">{{
        title
      }}</CardTitle>
      <CardDescription v-if="description" class="text-xs text-[var(--silver-400)] mt-1">
        {{ description }}
      </CardDescription>
    </CardHeader>
    <CardContent class="p-4">
      <div v-if="data.length === 0" class="text-center py-8 text-[var(--silver-400)] text-sm">
        {{ $t('common.noData') }}
      </div>
      <div v-else class="w-full flex justify-center overflow-x-auto">
        <div class="inline-flex flex-col">
          <!-- Column labels -->
          <div v-if="showLabels && columns" class="flex mb-1">
            <div class="w-12 shrink-0" />
            <!-- Spacer for row labels -->
            <div
              v-for="(col, index) in columns"
              :key="index"
              class="flex items-center justify-center text-2xs text-[var(--silver-400)] font-data tabular-nums"
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
              class="flex items-center"
              :style="{ marginBottom: rowIndex < gridData.maxY - 1 ? cellGap + 'px' : 0 }"
            >
              <!-- Row label -->
              <div
                v-if="showLabels && rows"
                class="shrink-0 w-12 text-right pr-2 text-2xs text-[var(--silver-400)] font-data tabular-nums"
              >
                {{ rows[rowIndex]?.label || '' }}
              </div>

              <!-- Cells -->
              <div class="flex">
                <div
                  v-for="(cell, colIndex) in row"
                  :key="colIndex"
                  class="rounded-none transition-all duration-150 cursor-default hover:ring-1 hover:ring-[var(--accent-primary)]"
                  :style="{
                    width: cellSize + 'px',
                    height: cellSize + 'px',
                    marginRight: colIndex < row.length - 1 ? cellGap + 'px' : 0,
                    backgroundColor: cell ? getCellColor(cell.value) : 'var(--silver-100)',
                    opacity: cell ? getCellOpacity(cell.value) : 0.3,
                  }"
                  @mouseenter="cell && handleMouseEnter(cell, $event)"
                  @mouseleave="handleMouseLeave"
                />
              </div>
            </div>
          </div>

          <!-- Legend -->
          <div
            class="flex items-center justify-end gap-2 mt-3 pt-3 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)]"
          >
            <span class="text-2xs text-[var(--silver-400)]">{{
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
                }"
              />
            </div>
            <span class="text-2xs text-[var(--silver-400)]">{{
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
            class="fixed z-50 px-2 py-1 text-xs rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-card shadow-lg pointer-events-none"
            :style="{
              left: tooltipPosition.x + 'px',
              top: tooltipPosition.y - 8 + 'px',
              transform: 'translate(-50%, -100%)',
            }"
          >
            <span class="font-data tabular-nums text-foreground">{{ hoveredCell.value }}</span>
            <span v-if="hoveredCell.label" class="text-[var(--silver-400)] ml-1">
              {{ hoveredCell.label }}
            </span>
          </div>
        </Transition>
      </Teleport>
    </CardContent>
  </Card>
</template>
