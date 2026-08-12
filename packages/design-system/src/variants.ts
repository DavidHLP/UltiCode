export const BUTTON_VARIANT_CLASSES = {
  default:
    "border border-primary bg-primary text-primary-foreground hover:bg-primary/90",
  destructive:
    "border border-destructive bg-status-error-surface text-foreground-strong hover:bg-status-error-surface/80 focus-visible:ring-destructive/30 [&_svg]:text-destructive",
  outline:
    "border border-control bg-background shadow-xs hover:bg-accent hover:text-accent-foreground dark:bg-input/30 dark:border-input dark:hover:bg-input/50",
  secondary: "bg-secondary text-secondary-foreground hover:bg-secondary/80",
  ghost: "hover:bg-accent hover:text-accent-foreground dark:hover:bg-accent/50",
  link: "text-link-foreground decoration-link-decoration underline underline-offset-4 hover:decoration-2",
} as const;

export const BADGE_VARIANT_CLASSES = {
  default:
    "border-primary bg-primary text-primary-foreground [a&]:hover:bg-primary/90",
  secondary:
    "border-transparent bg-secondary text-secondary-foreground [a&]:hover:bg-secondary/90",
  destructive:
    "border-destructive bg-status-error-surface text-foreground-strong [a&]:hover:bg-status-error-surface/80 focus-visible:ring-destructive/30 [&>svg]:text-destructive",
  outline:
    "text-foreground [a&]:hover:bg-accent [a&]:hover:text-accent-foreground",
} as const;

export const MENU_ITEM_VARIANT_CLASSES = {
  default:
    "focus:bg-accent focus:text-accent-foreground [&_svg:not([class*='text-'])]:text-muted-foreground",
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
