import type { Component } from "vue";

/**
 * Semantic status badge colors.
 * Shared official Badge rendering consumes their semantic theme tokens.
 */
export type SemanticColor =
  "success" | "warning" | "error" | "info" | "purple" | "electric" | "neutral";

/**
 * Options for the badge() helper function.
 */
export interface BadgeOptions {
  color: SemanticColor;
  label: string;
  icon?: Component;
  dot?: boolean;
  pulse?: boolean;
  size?: "xs" | "sm" | "md";
  class?: string;
}
