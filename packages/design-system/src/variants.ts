export const BUTTON_BASE_CLASSES =
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-all disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg:not([class*='size-'])]:size-4 shrink-0 [&_svg]:shrink-0 outline-none focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-3 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive";

export const BUTTON_VARIANT_CLASSES = {
  default:
    "border border-primary bg-primary text-primary-foreground hover:bg-primary/90",
  destructive:
    "border border-destructive bg-status-error-surface text-foreground-strong hover:bg-status-error-surface/80 focus-visible:ring-destructive/30 [&_svg]:text-destructive",
  outline:
    "border border-border-control bg-surface-elevated shadow-xs hover:border-primary hover:bg-surface-highlight hover:text-foreground-strong",
  secondary:
    "border border-transparent bg-surface-highlight text-foreground-strong hover:border-border-control hover:bg-surface-elevated",
  ghost: "hover:bg-surface-highlight hover:text-foreground-strong",
  link: "text-link-foreground decoration-link-decoration underline underline-offset-4 hover:decoration-2",
} as const;

export const BUTTON_SIZE_CLASSES = {
  default: "h-9 px-4 py-2 has-[>svg]:px-3",
  xs: "h-6 gap-1 px-2 text-xs has-[>svg]:px-1.5 [&_svg:not([class*='size-'])]:size-3",
  sm: "h-8 gap-1.5 px-3 has-[>svg]:px-2.5",
  lg: "h-10 px-6 has-[>svg]:px-4",
  icon: "size-9",
  "icon-xs": "size-6 [&_svg:not([class*='size-'])]:size-3",
  "icon-sm": "size-8",
  "icon-lg": "size-10",
} as const;

export const BADGE_VARIANT_CLASSES = {
  default:
    "border-primary bg-primary text-primary-foreground [a&]:hover:bg-primary/90",
  secondary:
    "border-transparent bg-secondary text-secondary-foreground [a&]:hover:bg-secondary/90",
  destructive:
    "border-destructive bg-status-error-surface text-foreground-strong [a&]:hover:bg-status-error-surface/80 focus-visible:ring-destructive/30 [&>svg]:text-destructive",
  outline:
    "text-foreground [a&]:hover:bg-surface-highlight [a&]:hover:text-foreground-strong",
} as const;

export const MENU_ITEM_VARIANT_CLASSES = {
  default:
    "focus:bg-surface-highlight focus:text-foreground-strong [&_svg:not([class*='text-'])]:text-muted-foreground",
  destructive:
    "text-foreground-strong focus:bg-status-error-surface [&_svg:not([class*='text-'])]:!text-destructive",
} as const;

const DIFFICULTY_BADGE_CLASSES: Record<string, string> = {
  EASY: "text-foreground-strong bg-status-success-surface border border-status-success-mark",
  MEDIUM: "text-foreground-strong bg-status-warning-surface border border-status-warning-mark",
  HARD: "text-foreground-strong bg-status-error-surface border border-status-error-mark",
};
const DEFAULT_DIFFICULTY_BADGE_CLASS =
  "text-foreground-strong bg-surface-highlight border border-control";

export function getDifficultyBadgeClass(difficulty: string): string {
  return DIFFICULTY_BADGE_CLASSES[difficulty.toUpperCase()] ?? DEFAULT_DIFFICULTY_BADGE_CLASS;
}
