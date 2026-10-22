<script setup lang="ts">
import { Button } from '@ulticode/design-system/ui/button'

import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { SidebarTrigger } from '@ulticode/design-system/ui/sidebar'
import { Separator } from '@ulticode/design-system/ui/separator'
import { Kbd } from '@ulticode/design-system/ui/kbd'
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
const shortcutLabel = /Mac|iPhone|iPad|iPod/.test(navigator.userAgent) ? '⌘ K' : 'Ctrl K'

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
  <header class="uc-app-header">
    <div class="uc-app-header-context">
      <SidebarTrigger class="uc-header-icon -ml-1" :aria-label="t('common.toggleSidebar')" />
      <Separator orientation="vertical" class="mr-2 hidden !h-4 md:block" />
      <Breadcrumb class="hidden min-w-0 md:block">
        <BreadcrumbList class="flex-nowrap">
          <template v-if="titleKey !== 'nav.dashboard'">
            <BreadcrumbItem class="hidden lg:block"
              ><BreadcrumbLink as-child
                ><RouterLink to="/">{{ t('nav.dashboard') }}</RouterLink></BreadcrumbLink
              ></BreadcrumbItem
            >
            <BreadcrumbSeparator class="hidden lg:block" />
          </template>
          <BreadcrumbItem class="min-w-0"
            ><BreadcrumbPage class="truncate">{{ t(titleKey) }}</BreadcrumbPage></BreadcrumbItem
          >
        </BreadcrumbList>
      </Breadcrumb>
    </div>

    <div class="uc-app-header-actions">
      <Button
        variant="outline"
        size="sm"
        class="uc-header-search"
        :aria-label="t('common.search')"
        @click="triggerSearch"
      >
        <IconSearch data-icon="inline-start" />
        <span class="hidden sm:inline">{{ t('common.search') }}</span>
        <Kbd class="ml-auto hidden lg:inline-flex">{{ shortcutLabel }}</Kbd>
      </Button>
      <!-- Quick Actions Menu -->
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <Button
            variant="ghost"
            class="uc-header-icon"
            :aria-label="t('common.actions.label')"
            :title="t('common.actions.label')"
          >
            <IconBolt class="size-4" />
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

      <!-- Notifications Bell -->
      <Button
        variant="ghost"
        @click="router.push('/notifications')"
        class="uc-header-icon"
        :aria-label="t('nav.notifications')"
        :title="t('nav.notifications')"
      >
        <IconBell class="size-4" />
      </Button>

      <ThemeSwitcher class="uc-header-icon" />
      <LanguageSwitcher />
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
