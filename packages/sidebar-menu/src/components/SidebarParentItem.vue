<script setup lang="ts">
import { Button } from "@ulticode/design-system/ui/button";

import { Button as NavigationButton } from "@ulticode/design-system/ui/button";
import type { Component } from "vue";
import {
  Collapsible as CollapsibleRoot,
  CollapsibleTrigger,
  CollapsibleContent,
} from "@ulticode/design-system/ui/collapsible";
import { cn } from "../utils";

/**
 * SidebarParentItem — a parent row that is both a link and a collapsible
 * group of children.
 *
 * - Mode A (`url` provided): the title is a `router-link` (navigates on
 *   click) and a separate chevron button toggles collapse. Matches console's
 *   "parent = link + collapsible children".
 * - Mode B (no `url`): the title row itself is the collapse trigger.
 *   Matches management's "parent = group-only (no navigation)".
 *
 * Uncontrolled only — open state is seeded from `defaultOpen` at mount and
 * then managed internally by reka. There is intentionally no `v-model:open`:
 * binding `:open="undefined"` makes reka treat CollapsibleRoot as
 * controlled-closed (see commit fc266ce10). If you need route-driven
 * auto-expand, use `SidebarGroupCollapsible` (which forwards `open`
 * conditionally) instead.
 *
 * Security: `url` must be a trusted internal route string. Do NOT bind
 * user-supplied input directly — vue-router does not block `javascript:` URLs.
 */
const props = withDefaults(
  defineProps<{
    title: string;
    url?: string;
    /** Leading icon component (lucide in console, tabler in management). */
    icon?: Component;
    /** Extra class applied to the leading icon (e.g. active color). */
    iconClass?: string;
    active?: boolean;
    defaultOpen?: boolean;
    class?: string;
  }>(),
  { active: false, defaultOpen: true },
);

const rowBase = "flex-1 justify-start";
</script>

<template>
  <CollapsibleRoot
    :default-open="defaultOpen"
    data-slot="collapsible"
    class="group/collapsible"
    v-slot="{ open: isOpen }"
  >
    <div :class="cn('group flex items-center', props.class)">
      <!-- Mode A: title navigates; chevron is a separate collapse trigger. -->
      <template v-if="url">
        <NavigationButton
          variant="ghost"
          as-child
          :class="rowBase"
          data-sidebar="menu-button"
          :data-active="active || undefined"
          ><component
            :is="'router-link'"
            :to="url"
            data-sidebar="menu-button"
            :data-active="active || undefined"
            :class="rowBase"
          >
            <component
              :is="icon"
              v-if="icon"
              :class="cn('size-4 shrink-0 transition-colors', iconClass)"
            />
            <span class="flex-1 truncate">{{ title }}</span>
          </component></NavigationButton
        >
        <CollapsibleTrigger as-child>
          <Button
            variant="ghost"
            type="button"
            class="mr-1 flex shrink-0 items-center justify-center min-h-11 min-w-11 sm:min-h-7 sm:min-w-7"
            :aria-label="isOpen ? 'collapse section' : 'expand section'"
          >
            <slot name="chevron" :open="isOpen">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                :class="
                  cn(
                    'size-4 transition-transform duration-200',
                    isOpen && 'rotate-90',
                  )
                "
              >
                <path d="m9 18 6-6-6-6" />
              </svg>
            </slot>
          </Button>
        </CollapsibleTrigger>
      </template>
      <!-- Mode B: title row is the collapse trigger itself. -->
      <CollapsibleTrigger v-else as-child>
        <Button
          variant="ghost"
          type="button"
          data-sidebar="menu-button"
          :class="rowBase"
          :aria-label="isOpen ? 'collapse section' : 'expand section'"
        >
          <component
            :is="icon"
            v-if="icon"
            :class="cn('size-4 shrink-0 transition-colors', iconClass)"
          />
          <span class="flex-1 truncate text-left">{{ title }}</span>
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            :class="
              cn(
                'ml-auto size-4 shrink-0 text-muted-foreground transition-transform duration-200',
                isOpen && 'rotate-90',
              )
            "
          >
            <path d="m9 18 6-6-6-6" />
          </svg>
        </Button>
      </CollapsibleTrigger>
    </div>
    <CollapsibleContent>
      <slot :open="isOpen" />
    </CollapsibleContent>
  </CollapsibleRoot>
</template>
