<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSearchPalette } from '@/composables/useSearchPalette'
import {
  IconSearch,
  IconBell,
  IconBolt,
  IconChevronDown,
  IconPlus,
  IconSettings,
} from '@tabler/icons-vue'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  CommandDialog,
  CommandInput,
  CommandList,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandShortcut,
  CommandSeparator,
} from '@/components/ui/command'

const router = useRouter()
const { t } = useI18n()

const { isOpen: isSearchOpen, toggle: toggleSearch } = useSearchPalette()

/**
 * Navigation shortcuts. The key is matched case-insensitively against
 * `e.key` after stripping the modifier (so "D" and "d" both work).
 * Keep this in sync with the `<CommandShortcut>` labels rendered inside
 * the palette.
 */
const shortcutRoutes: Record<string, string> = {
  d: '/',
  u: '/users',
  p: '/problems',
  c: '/contests',
  a: '/audit',
  y: '/analytics',
  s: '/settings',
}

/** Targets where typing must not be hijacked by global shortcuts. */
function isEditableTarget(target: EventTarget | null): boolean {
  if (!target || !(target instanceof HTMLElement)) return false
  const tag = target.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true
  if (target.isContentEditable) return true
  return false
}

function triggerSearch() {
  isSearchOpen.value = true
}

function handleKeyDown(e: KeyboardEvent) {
  // Ignore IME composition, key auto-repeat, and Alt-modified chords
  // so we never break native text entry or browser menu shortcuts.
  if (e.isComposing || e.repeat || e.altKey) return
  // Never hijack typing in form fields or contenteditable surfaces.
  if (isEditableTarget(e.target)) return

  const modifier = e.metaKey || e.ctrlKey
  if (!modifier) return

  const key = e.key.toLowerCase()

  // Toggle palette
  if (key === 'k') {
    e.preventDefault()
    toggleSearch()
    return
  }

  // Navigation shortcuts
  const path = shortcutRoutes[key]
  if (path !== undefined) {
    // preventDefault covers browser-native actions on these chords:
    //   Ctrl/Cmd+D = bookmark, U = view source, P = print, S = save,
    //   A = select all, C = copy, Y = redo. Keeping them in-page only.
    e.preventDefault()
    isSearchOpen.value = false
    router.push(path)
  }
}

function goTo(path: string) {
  isSearchOpen.value = false
  router.push(path)
}

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown)
})
</script>

<template>
  <header
    class="flex h-14 shrink-0 items-center border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/40 bg-[var(--card)] transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-14 select-none z-10"
  >
    <div class="flex w-full items-center justify-between px-4 lg:px-6">
      <!-- Left Side: Sidebar Trigger, Search & System Status -->
      <div class="flex items-center gap-4">
        <!-- Search Trigger Button -->
        <button
          class="flex items-center justify-between w-48 md:w-64 h-8 px-3 text-xs text-[var(--foreground-muted)] border border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/50 bg-[var(--surface-highlight)]/30 hover:bg-[var(--surface-highlight)]/70 hover:border-[var(--accent-primary)] hover:text-[var(--accent-primary)] transition-all rounded-none font-mono cursor-pointer outline-none focus-visible:ring-1 focus-visible:ring-ring"
          @click="triggerSearch"
        >
          <div class="flex items-center gap-2">
            <IconSearch class="size-3.5 text-[var(--foreground-muted)]" />
            <span>{{ t('common.search') }}...</span>
          </div>
          <kbd
            class="pointer-events-none inline-flex h-4 select-none items-center gap-0.5 border border-[var(--border-subtle)] bg-[var(--card)] px-1 font-mono text-2xs font-medium text-[var(--foreground-muted)] shrink-0"
          >
            Ctrl+K
          </kbd>
        </button>
      </div>

      <!-- Right Side: System Status, Quick Actions, Notifications, Language Switcher -->
      <div class="flex items-center gap-4">
        <!-- System Telemetry Status (retro styled badge) -->
        <div
          class="hidden sm:flex items-center gap-3 h-8 px-2.5 border border-[var(--border-subtle)]/80 dark:border-[var(--border-subtle)]/40 bg-transparent text-2xs font-mono select-none"
        >
          <div class="flex items-center gap-1.5">
            <span class="relative flex h-1.5 w-1.5 shrink-0">
              <span
                class="animate-ping absolute inline-flex h-full w-full rounded-none bg-[var(--status-success-mark)] opacity-75"
              ></span>
              <span
                class="relative inline-flex rounded-none h-1.5 w-1.5 bg-[var(--status-success-mark)]"
              ></span>
            </span>
            <span class="text-[var(--foreground-muted)] font-bold">API</span>
          </div>
          <span class="h-3 w-px bg-[var(--border-subtle)] dark:bg-[var(--border-subtle)]/40"></span>
          <div class="flex items-center gap-1.5">
            <span class="h-1.5 w-1.5 bg-[var(--status-success-mark)] shrink-0"></span>
            <span class="text-[var(--foreground-muted)] font-bold">DB</span>
          </div>
        </div>

        <!-- Vertical Divider -->
        <span
          class="hidden sm:inline-block h-4 w-px bg-[var(--border-subtle)]/60 dark:bg-[var(--border-subtle)]/40"
        ></span>

        <!-- Quick Actions Menu -->
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <button
              class="flex items-center gap-1.5 h-8 px-3 text-xs font-mono border border-[var(--accent-primary)]/40 bg-transparent hover:bg-[var(--accent-primary)]/5 hover:border-[var(--accent-primary)] text-[var(--accent-primary)] transition-all rounded-none cursor-pointer outline-none focus-visible:ring-1 focus-visible:ring-ring"
            >
              <IconBolt
                class="size-3.5 text-[var(--status-warning-mark)] fill-[var(--status-warning-mark)]/20"
              />
              <span class="font-bold uppercase">{{ t('common.actions.label') }}</span>
              <IconChevronDown class="size-3 text-[var(--accent-primary)]" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent variant="terminal" align="end" class="w-48">
            <DropdownMenuLabel class="font-mono text-2xs text-[var(--foreground-muted)] uppercase">{{
              t('dashboard.quickActions.title')
            }}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem variant="terminal" @click="router.push('/problems')">
              <IconPlus class="size-3.5 mr-2 text-[var(--foreground-muted)]" />
              <span>{{ t('dashboard.quickActions.createProblem') }}</span>
            </DropdownMenuItem>
            <DropdownMenuItem variant="terminal" @click="router.push('/contests')">
              <IconPlus class="size-3.5 mr-2 text-[var(--foreground-muted)]" />
              <span>{{ t('dashboard.quickActions.createContest') }}</span>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem variant="terminal" @click="router.push('/settings')">
              <IconSettings class="size-3.5 mr-2 text-[var(--foreground-muted)]" />
              <span>{{ t('nav.settings') }}</span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        <!-- Vertical Divider -->
        <span class="h-4 w-px bg-[var(--border-subtle)]/60 dark:bg-[var(--border-subtle)]/40"></span>

        <!-- Notifications Bell -->
        <button
          @click="router.push('/notifications')"
          class="relative h-8 w-8 flex items-center justify-center text-[var(--foreground-muted)] hover:text-[var(--accent-primary)] border border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/50 bg-transparent hover:bg-[var(--surface-highlight)]/50 hover:border-[var(--accent-primary)] transition-all rounded-none cursor-pointer outline-none focus-visible:ring-1 focus-visible:ring-ring"
          :title="t('nav.notifications')"
        >
          <IconBell class="size-4" />
          <span
            class="absolute top-1.5 right-1.5 h-1.5 w-1.5 bg-[var(--status-error-mark)] rounded-none"
          ></span>
        </button>

        <!-- Vertical Divider -->
        <span class="h-4 w-px bg-[var(--border-subtle)]/60 dark:bg-[var(--border-subtle)]/40"></span>

        <!-- Theme Switcher -->
        <ThemeSwitcher />

        <!-- Vertical Divider -->
        <span class="h-4 w-px bg-[var(--border-subtle)]/60 dark:bg-[var(--border-subtle)]/40"></span>

        <!-- Language Switcher -->
        <div>
          <LanguageSwitcher />
        </div>
      </div>
    </div>
  </header>

  <!-- Search Command Dialog Palette -->
  <CommandDialog v-model:open="isSearchOpen">
    <CommandInput :placeholder="t('common.search') + '...'" />
    <CommandList>
      <CommandEmpty>No results found.</CommandEmpty>
      <CommandGroup heading="Navigation">
        <CommandItem value="dashboard" @select="goTo('/')">
          <span>{{ t('nav.dashboard') }}</span>
          <CommandShortcut>Ctrl+D</CommandShortcut>
        </CommandItem>
        <CommandItem value="users" @select="goTo('/users')">
          <span>{{ t('nav.users') }}</span>
          <CommandShortcut>Ctrl+U</CommandShortcut>
        </CommandItem>
        <CommandItem value="problems" @select="goTo('/problems')">
          <span>{{ t('nav.problems') }}</span>
          <CommandShortcut>Ctrl+P</CommandShortcut>
        </CommandItem>
        <CommandItem value="contests" @select="goTo('/contests')">
          <span>{{ t('nav.contests') }}</span>
          <CommandShortcut>Ctrl+C</CommandShortcut>
        </CommandItem>
      </CommandGroup>
      <CommandSeparator />
      <CommandGroup heading="System">
        <CommandItem value="audit" @select="goTo('/audit')">
          <span>{{ t('nav.auditLogs') }}</span>
          <CommandShortcut>Ctrl+A</CommandShortcut>
        </CommandItem>
        <CommandItem value="analytics" @select="goTo('/analytics')">
          <span>{{ t('nav.analytics') }}</span>
          <CommandShortcut>Ctrl+Y</CommandShortcut>
        </CommandItem>
        <CommandItem value="settings" @select="goTo('/settings')">
          <span>{{ t('nav.settings') }}</span>
          <CommandShortcut>Ctrl+S</CommandShortcut>
        </CommandItem>
      </CommandGroup>
    </CommandList>
  </CommandDialog>
</template>
