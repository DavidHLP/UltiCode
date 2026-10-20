const DIFFICULTY_BADGE_CLASSES: Record<string, string> = {
  EASY: "text-foreground bg-status-success-surface border border-status-success-mark",
  MEDIUM:
    "text-foreground bg-status-warning-surface border border-status-warning-mark",
  HARD: "text-foreground bg-status-error-surface border border-status-error-mark",
};
const DEFAULT_DIFFICULTY_BADGE_CLASS =
  "text-foreground bg-accent border border-input";

export function getDifficultyBadgeClass(difficulty: string): string {
  return (
    DIFFICULTY_BADGE_CLASSES[difficulty.toUpperCase()] ??
    DEFAULT_DIFFICULTY_BADGE_CLASS
  );
}
