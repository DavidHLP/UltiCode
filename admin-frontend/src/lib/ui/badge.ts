import type { Component } from 'vue'
import { h } from 'vue'
import { Badge } from '@/components/ui/badge'
import type { BadgeVariant } from '@/lib/entities/user'

export interface BadgeConfig {
  label: string | ((value: string) => string)
  variant: BadgeVariant | ((value: string) => BadgeVariant)
  icon?: Component
  colorClass?: string | ((value: string) => string)
}

/**
 * Creates a badge component from configuration
 * @param value - The value to create a badge for
 * @param config - Badge configuration
 * @returns Badge component or span element
 */
export function createBadge(value: string, config: BadgeConfig): Component {
  const variant = typeof config.variant === 'function' ? config.variant(value) : config.variant

  const label = typeof config.label === 'function' ? config.label(value) : config.label

  const colorClass =
    typeof config.colorClass === 'function' ? config.colorClass(value) : config.colorClass

  const children = [config.icon ? h(config.icon, { class: 'h-3 w-3 mr-1' }) : null, label].filter(
    Boolean,
  )

  const badgeProps: Record<string, unknown> = { variant }
  if (colorClass) {
    badgeProps.class = colorClass
  }

  return h(Badge, badgeProps, () => children)
}

/**
 * Creates a badge configuration map from a simple object
 * @param configMap - Object mapping values to badge configurations
 * @returns Function that returns BadgeConfig for a given value
 */
export function createBadgeConfigMapper(
  configMap: Record<string, Omit<BadgeConfig, 'label'> & { label?: string }>,
  defaultLabel?: string,
): (value: string) => BadgeConfig {
  return (value: string) => {
    const config = configMap[value]
    return {
      label: config?.label || defaultLabel || value,
      variant: config?.variant || 'outline',
      icon: config?.icon,
      colorClass: config?.colorClass,
    }
  }
}

/**
 * Common badge variants
 */
export const BadgeVariants = {
  DEFAULT: 'default' as BadgeVariant,
  SECONDARY: 'secondary' as BadgeVariant,
  DESTRUCTIVE: 'destructive' as BadgeVariant,
  OUTLINE: 'outline' as BadgeVariant,
} as const

/**
 * Common badge color classes
 */
export const BadgeColorClasses = {
  SUCCESS: 'text-emerald-500 bg-emerald-500/10 border-emerald-500/20',
  WARNING: 'text-amber-500 bg-amber-500/10 border-amber-500/20',
  ERROR: 'text-red-500 bg-red-500/10 border-red-500/20',
  INFO: 'text-blue-500 bg-blue-500/10 border-blue-500/20',
  MUTED: 'text-muted-foreground',
} as const
