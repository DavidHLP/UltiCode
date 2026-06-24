<script setup lang="ts">
import type { Component } from 'vue'
import { CollapsibleRoot, CollapsibleTrigger, CollapsibleContent } from 'reka-ui'
import { cn } from '../utils'

const props = withDefaults(
  defineProps<{
    title: string
    /**
     * When provided, the title row is a router-link (navigates on click) and a
     * separate chevron toggles collapse — matches console's "parent = link +
     * collapsible children". When absent, the title row itself is the collapse
     * trigger — matches management's "parent = group-only (no navigation)".
     */
    url?: string
    /** Leading icon component (lucide in console, tabler in management). */
    icon?: Component
    /** Extra class applied to the leading icon (e.g. active color). */
    iconClass?: string
    active?: boolean
    defaultOpen?: boolean
    class?: string
  }>(),
  { active: false, defaultOpen: true },
)

const rowBase =
  'uc-sidebar-item group flex flex-1 cursor-pointer items-center gap-2.5 rounded-md mx-1 h-9 pl-2.5 pr-3 py-1.5 text-sm font-medium transition-all duration-200 select-none'
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
        <component
          :is="'router-link'"
          :to="url"
          :data-active="active ? 'true' : 'false'"
          :class="rowBase"
        >
          <component :is="icon" v-if="icon" :class="cn('size-4 shrink-0 transition-colors', iconClass)" />
          <span class="flex-1 truncate">{{ title }}</span>
        </component>
        <CollapsibleTrigger as-child>
          <button
            type="button"
            class="mr-1 flex size-7 shrink-0 items-center justify-center rounded-md text-[var(--silver-500)] hover:bg-[var(--silver-200)]/40 hover:text-[var(--foreground)]"
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
                :class="cn('size-4 transition-transform duration-200', isOpen && 'rotate-90')"
              >
                <path d="m9 18 6-6-6-6" />
              </svg>
            </slot>
          </button>
        </CollapsibleTrigger>
      </template>
      <!-- Mode B: title row is the collapse trigger itself. -->
      <CollapsibleTrigger v-else as-child>
        <button
          type="button"
          :data-active="active ? 'true' : 'false'"
          :class="rowBase"
          :aria-label="isOpen ? 'collapse section' : 'expand section'"
        >
          <component :is="icon" v-if="icon" :class="cn('size-4 shrink-0 transition-colors', iconClass)" />
          <span class="flex-1 truncate text-left">{{ title }}</span>
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            :class="cn('ml-auto size-4 shrink-0 text-[var(--silver-500)] transition-transform duration-200', isOpen && 'rotate-90')"
          >
            <path d="m9 18 6-6-6-6" />
          </svg>
        </button>
      </CollapsibleTrigger>
    </div>
    <CollapsibleContent>
      <slot :open="isOpen" />
    </CollapsibleContent>
  </CollapsibleRoot>
</template>
