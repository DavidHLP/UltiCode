<script setup lang="ts">
import { Button } from '@ulticode/design-system/ui/button'

import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { SidebarTrigger } from '@ulticode/design-system/ui/sidebar'
import { Separator } from '@ulticode/design-system/ui/separator'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@ulticode/design-system/ui/breadcrumb'
import { useI18n } from 'vue-i18n'
import { useSearchPalette } from '@/composables/useSearchPalette'
import {
  Search as IconSearch,
  Bell as IconBell,
  Bolt as IconBolt,
  ChevronDown as IconChevronDown,
  Plus as IconPlus,
  Settings as IconSettings,
} from '@lucide/vue'

import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  DropdownMenuGroup,
} from '@ulticode/design-system/ui/dropdown-menu'
import {
  CommandDialog,
  CommandInput,
  CommandList,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandShortcut,
  CommandSeparator,
} from '@ulticode/design-system/ui/command'

const router = useRouter()
const route = useRoute()
const titleKey = computed(() =>
  typeof route.meta.titleKey === 'string' ? route.meta.titleKey : 'nav.dashboard',
)
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
    class="flex min-h-14 shrink-0 items-center border-b border-border dark:border-border/40 bg-card transition-[width,height] ease-linear select-none z-10"
  >
    <div class="flex w-full flex-wrap items-center justify-between gap-y-2 px-4 py-2 lg:px-6">
      <!-- Left Side: Sidebar Trigger, Search & System Status -->
      <div class="flex items-center gap-2">
        <SidebarTrigger :aria-label="t('common.toggleSidebar')" />
        <Separator orientation="vertical" class="h-4" />
        <Breadcrumb class="hidden xl:block">
          <BreadcrumbList>
            <template v-if="titleKey !== 'nav.dashboard'">
              <BreadcrumbItem
                ><BreadcrumbLink as-child
                  ><RouterLink to="/">{{ t('nav.dashboard') }}</RouterLink></BreadcrumbLink
                ></BreadcrumbItem
              >
              <BreadcrumbSeparator />
            </template>
            <BreadcrumbItem
              ><BreadcrumbPage>{{ t(titleKey) }}</BreadcrumbPage></BreadcrumbItem
            >
          </BreadcrumbList>
        </Breadcrumb>
        <!-- Search Trigger Button -->
        <Button
          variant="outline"
          class="size-9 px-2 sm:w-48 sm:justify-between sm:px-3"
          :aria-label="t('common.search')"
          @click="triggerSearch"
        >
          <div class="flex items-center gap-2">
            <IconSearch class="size-3.5 text-muted-foreground" />
            <span class="hidden sm:inline">{{ t('common.search') }}...</span>
          </div>
          <kbd
            class="pointer-events-none hidden md:inline-flex h-4 select-none items-center gap-0.5 border border-border bg-card px-1 font-mono text-xs font-medium text-muted-foreground shrink-0"
          >
            Ctrl+K
          </kbd>
        </Button>
      </div>

      <!-- Right Side: System Status, Quick Actions, Notifications, Language Switcher -->
      <div class="ml-auto flex items-center gap-2">
        <!-- Quick Actions Menu -->
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button
              variant="ghost"
              class="flex items-center gap-1.5 px-3 cursor-pointer outline-none"
            >
              <IconBolt class="size-3.5 text-status-warning-mark fill-status-warning-mark/20" />
              <span class="hidden md:inline">{{ t('common.actions.label') }}</span>
              <IconChevronDown class="size-3 text-primary" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="w-48">
            <DropdownMenuGroup
              ><DropdownMenuLabel>{{ t('dashboard.quickActions.title') }}</DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem @click="router.push('/problems')">
                <IconPlus class="size-3.5 mr-2 text-muted-foreground" />
                <span>{{ t('dashboard.quickActions.createProblem') }}</span>
              </DropdownMenuItem>
              <DropdownMenuItem @click="router.push('/contests')">
                <IconPlus class="size-3.5 mr-2 text-muted-foreground" />
                <span>{{ t('dashboard.quickActions.createContest') }}</span>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem @click="router.push('/settings')">
                <IconSettings class="size-3.5 mr-2 text-muted-foreground" />
                <span>{{ t('nav.settings') }}</span>
              </DropdownMenuItem></DropdownMenuGroup
            >
          </DropdownMenuContent>
        </DropdownMenu>

        <!-- Vertical Divider -->
        <span class="h-4 w-px bg-border/60 dark:bg-border/40"></span>

        <!-- Notifications Bell -->
        <Button
          variant="ghost"
          @click="router.push('/notifications')"
          class="relative w-8 flex items-center justify-center cursor-pointer outline-none"
          :title="t('nav.notifications')"
        >
          <IconBell class="size-4" />
        </Button>

        <!-- Vertical Divider -->
        <span class="h-4 w-px bg-border/60 dark:bg-border/40"></span>

        <!-- Theme Switcher -->
        <ThemeSwitcher />

        <!-- Vertical Divider -->
        <span class="h-4 w-px bg-border/60 dark:bg-border/40"></span>

        <!-- Language Switcher -->
        <div>
          <LanguageSwitcher />
        </div>
      </div>
    </div>
  </header>

  <!-- Search Command Dialog Palette -->
  <CommandDialog
    v-model:open="isSearchOpen"
    :title="t('common.search')"
    :description="t('common.searchDescription')"
  >
    <CommandInput :placeholder="t('common.search') + '...'" />
    <CommandList>
      <CommandEmpty>{{ t('common.noResults') }}</CommandEmpty>
      <CommandGroup :heading="t('common.navigation')">
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
      <CommandGroup :heading="t('common.system')">
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
