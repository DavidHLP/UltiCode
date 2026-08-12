import { cva } from "class-variance-authority";

export { default as NavigationMenu } from "./NavigationMenu.vue";
export { default as NavigationMenuContent } from "./NavigationMenuContent.vue";
export { default as NavigationMenuIndicator } from "./NavigationMenuIndicator.vue";
export { default as NavigationMenuItem } from "./NavigationMenuItem.vue";
export { default as NavigationMenuLink } from "./NavigationMenuLink.vue";
export { default as NavigationMenuList } from "./NavigationMenuList.vue";
export { default as NavigationMenuTrigger } from "./NavigationMenuTrigger.vue";
export { default as NavigationMenuViewport } from "./NavigationMenuViewport.vue";

export const navigationMenuTriggerStyle = cva(
  "group inline-flex h-9 w-max items-center justify-center rounded-md bg-surface-elevated px-4 py-2 text-sm font-medium text-foreground-muted transition-[color,background-color,box-shadow] hover:bg-surface-highlight hover:text-foreground-strong focus:bg-surface-highlight focus:text-foreground-strong disabled:pointer-events-none disabled:opacity-50 data-[state=open]:bg-surface-highlight data-[state=open]:text-foreground-strong focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none",
);
