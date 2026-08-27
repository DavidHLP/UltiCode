import type { VariantProps } from "class-variance-authority";
import {
  BUTTON_BASE_CLASSES,
  BUTTON_SIZE_CLASSES,
  BUTTON_VARIANT_CLASSES,
} from "@ulticode/design-system";
import { cva } from "class-variance-authority";

export { default as Button } from "./Button.vue";

export const buttonVariants = cva(BUTTON_BASE_CLASSES, {
  variants: {
    variant: {
      ...BUTTON_VARIANT_CLASSES,
    },
    size: BUTTON_SIZE_CLASSES,
  },
  defaultVariants: {
    variant: "default",
    size: "default",
  },
});
export type ButtonVariants = VariantProps<typeof buttonVariants>;
