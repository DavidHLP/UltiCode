import type { VariantProps } from 'class-variance-authority'
import { cva } from 'class-variance-authority'

export { default as Button } from './Button.vue'

export const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap text-sm font-medium transition-all disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg:not([class*='size-'])]:size-4 shrink-0 [&_svg]:shrink-0 outline-none focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px] aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive",
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground hover:bg-primary/90',
        destructive:
          'bg-destructive text-white hover:bg-destructive/90 focus-visible:ring-destructive/20 dark:focus-visible:ring-destructive/40 dark:bg-destructive/60',
        outline:
          'border bg-background shadow-xs hover:bg-accent hover:text-accent-foreground dark:bg-input/30 dark:border-input dark:hover:bg-input/50',
        secondary: 'bg-secondary text-secondary-foreground hover:bg-secondary/80',
        ghost: 'hover:bg-accent hover:text-accent-foreground dark:hover:bg-accent/50',
        link: 'text-primary underline-offset-4 hover:underline',
        // Terminal Precision variants
        terminal:
          'border border-[var(--silver-300)] bg-transparent font-data text-xs uppercase tracking-wider text-[var(--silver-600)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] dark:border-[var(--silver-300)] dark:text-[var(--silver-400)] dark:hover:border-[var(--accent-electric)] dark:hover:text-[var(--accent-electric)] transition-colors rounded-none shadow-none',
        terminal_primary:
          'bg-[var(--accent-electric)] text-white font-data text-xs uppercase tracking-wider hover:bg-[var(--accent-electric)]/90 dark:hover:bg-[var(--accent-electric)]/80 rounded-none shadow-none',
        terminal_danger:
          'border border-[var(--terminal-red)] bg-transparent text-[var(--terminal-red)] font-data text-xs uppercase tracking-wider hover:bg-[oklch(0.6_0.2_25/0.1)] dark:hover:bg-[oklch(0.58_0.18_25/0.15)] rounded-none shadow-none',
        terminal_ghost:
          'font-data text-xs uppercase tracking-wider text-[var(--silver-500)] hover:text-[var(--foreground)] hover:bg-transparent dark:text-[var(--silver-400)] dark:hover:text-[var(--foreground)] rounded-none shadow-none',
      },
      size: {
        default: 'h-9 px-4 py-2 has-[>svg]:px-3',
        sm: 'h-8 gap-1.5 px-3 has-[>svg]:px-2.5',
        lg: 'h-10 px-6 has-[>svg]:px-4',
        icon: 'size-9',
        'icon-sm': 'size-8',
        'icon-lg': 'size-10',
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
  },
)
export type ButtonVariants = VariantProps<typeof buttonVariants>
