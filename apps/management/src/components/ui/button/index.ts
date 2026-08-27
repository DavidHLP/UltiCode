import type { VariantProps } from 'class-variance-authority'
import {
  BUTTON_BASE_CLASSES,
  BUTTON_SIZE_CLASSES,
  BUTTON_VARIANT_CLASSES,
} from '@ulticode/design-system'
import { cva } from 'class-variance-authority'

export { default as Button } from './Button.vue'

export const buttonVariants = cva(BUTTON_BASE_CLASSES, {
  variants: {
    variant: {
      ...BUTTON_VARIANT_CLASSES,
      // Terminal Precision variants
      terminal:
        'border border-[var(--border-subtle)] bg-transparent font-data text-xs uppercase tracking-wider text-[var(--foreground-strong)] hover:border-[var(--primary)] hover:text-foreground dark:border-[var(--border-subtle)] dark:text-[var(--foreground-muted)] dark:hover:border-[var(--primary)] dark:hover:text-foreground transition-colors rounded-none shadow-none',
      terminal_primary:
        'border border-[var(--accent-primary)] bg-primary text-primary-foreground font-data text-xs uppercase tracking-wider hover:bg-primary/90 rounded-none shadow-none',
      terminal_danger:
        'border border-[var(--status-error-mark)] bg-status-error-surface text-foreground-strong font-data text-xs uppercase tracking-wider hover:bg-status-error-surface/80 [&_svg]:text-foreground-strong rounded-none shadow-none',
      terminal_ghost:
        'font-data text-xs uppercase tracking-wider text-[var(--foreground-muted)] hover:text-[var(--foreground)] hover:bg-transparent dark:text-[var(--foreground-muted)] dark:hover:text-[var(--foreground)] rounded-none shadow-none',
    },
    size: {
      ...BUTTON_SIZE_CLASSES,
      // Terminal sizes
      terminal: 'h-8 px-3 py-1.5',
      terminal_sm: 'h-7 px-2.5 py-1 text-2xs',
      terminal_lg: 'h-9 px-4 py-2',
    },
  },
  defaultVariants: {
    variant: 'default',
    size: 'default',
  },
})
export type ButtonVariants = VariantProps<typeof buttonVariants>
