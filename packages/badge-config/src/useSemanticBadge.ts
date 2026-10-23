import { h, type VNode } from "vue";
import { Badge } from "@ulticode/design-system/ui/badge";
import { joinClasses } from "./utils/cn";
import type { BadgeOptions, SemanticColor } from "./semantic-colors";

const COLOR_CLASS_MAP: Record<SemanticColor, string> = {
  success: "text-status-success-mark bg-status-success-surface",
  warning: "text-status-warning-mark bg-status-warning-surface",
  error: "text-status-error-mark bg-status-error-surface",
  info: "text-status-info-mark bg-status-info-surface",
  purple: "text-status-special-mark bg-status-special-surface",
  electric: "text-status-info-mark bg-status-info-surface",
  neutral: "bg-secondary text-secondary-foreground",
};

const SIZE_CLASSES = {
  xs: "text-xs px-1.5 py-0.5",
  sm: "text-xs px-2 py-0.5",
  md: "text-sm px-2 py-0.5",
} as const;

const DOT_COLOR_MAP: Record<SemanticColor, string> = {
  success: "bg-status-success-mark",
  warning: "bg-status-warning-mark",
  error: "bg-status-error-mark",
  info: "bg-status-info-mark",
  purple: "bg-status-special-mark",
  electric: "bg-primary",
  neutral: "bg-muted-foreground",
};

/**
 * Creates a semantic badge VNode for use in column render functions.
 *
 * Drop-in replacement for all local renderStatusBadge/renderDifficultyBadge functions.
 * Composes the shared official Badge with semantic status tokens.
 */
export function badge(options: BadgeOptions): VNode {
  const { color, label, icon, dot, pulse, size = "md" } = options;
  const children: (VNode | string)[] = [];

  if (dot) {
    children.push(
      h("span", {
        class: joinClasses(
          "w-1.5 h-1.5 rounded-full shrink-0",
          DOT_COLOR_MAP[color],
          pulse && "",
        ),
      }),
    );
  }

  if (icon) {
    children.push(h(icon, { class: "h-3.5 w-3.5 shrink-0" }));
  }

  children.push(label);

  return h(
    Badge,
    {
      variant: "secondary",
      class: joinClasses(
        COLOR_CLASS_MAP[color],
        SIZE_CLASSES[size],
        pulse && !dot && "",
        options.class,
      ),
    },
    () => children,
  );
}
