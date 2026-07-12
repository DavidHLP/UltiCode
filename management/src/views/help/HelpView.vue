<script setup lang="ts">
/**
 * HelpView - In-app help & shortcuts reference.
 *
 * Lightweight landing surface for the sidebar "获取帮助" / "Get Help"
 * entry. Renders the global keyboard shortcuts wired up in SiteHeader,
 * the app version pulled from Vite's build-time env, and a small set of
 * shortcuts to common admin actions.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/stores/auth'
import {
  IconKeyboard,
  IconBolt,
  IconChartBar,
  IconUsers,
  IconFlag,
  IconBook,
  IconExternalLink,
} from '@tabler/icons-vue'

defineOptions({ name: 'HelpView' })

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

/** Build-time injected by Vite. Empty string when not built. */
const appVersion = computed(() => (import.meta.env.VITE_APP_VERSION as string | undefined) || 'dev')
const buildTime = computed(() => (import.meta.env.VITE_BUILD_TIME as string | undefined) || '—')

interface Shortcut {
  keys: string[]
  descriptionKey: string
}

const shortcuts: Shortcut[] = [
  { keys: ['Ctrl', 'K'], descriptionKey: 'help.shortcuts.openSearch' },
  { keys: ['Ctrl', 'D'], descriptionKey: 'help.shortcuts.goDashboard' },
  { keys: ['Ctrl', 'U'], descriptionKey: 'help.shortcuts.goUsers' },
  { keys: ['Ctrl', 'P'], descriptionKey: 'help.shortcuts.goProblems' },
  { keys: ['Ctrl', 'C'], descriptionKey: 'help.shortcuts.goContests' },
  { keys: ['Ctrl', 'A'], descriptionKey: 'help.shortcuts.goAudit' },
  { keys: ['Ctrl', 'Y'], descriptionKey: 'help.shortcuts.goAnalytics' },
  { keys: ['Ctrl', 'S'], descriptionKey: 'help.shortcuts.goSettings' },
]

const quickLinks = computed(() => {
  const links = [{ to: '/', icon: IconChartBar, labelKey: 'help.quickLinks.dashboard' }]
  if (authStore.hasPermission('READ', 'USER')) {
    links.push({ to: '/users', icon: IconUsers, labelKey: 'help.quickLinks.users' })
  }
  if (authStore.hasPermission('MODERATE', 'PROBLEM')) {
    links.push({ to: '/moderation', icon: IconFlag, labelKey: 'help.quickLinks.moderation' })
  }
  if (authStore.hasPermission('READ', 'ANALYTICS')) {
    links.push({ to: '/analytics', icon: IconBolt, labelKey: 'help.quickLinks.analytics' })
  }
  return links
})

function go(to: string) {
  router.push(to)
}
</script>

<template>
  <div class="flex flex-col gap-6 py-6 px-4 lg:px-8 bg-background">
    <!-- Header -->
    <header
      class="flex flex-col gap-2 pb-4 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]"
    >
      <div class="flex items-center gap-3">
        <IconBook class="size-6 text-[var(--accent-primary)]" />
        <h1 class="text-2xl font-medium tracking-tight text-foreground">
          {{ t('help.title') }}
        </h1>
      </div>
      <p class="text-sm text-[var(--silver-500)]">{{ t('help.description') }}</p>
    </header>

    <div class="grid grid-cols-1 gap-5 lg:grid-cols-3">
      <!-- Keyboard shortcuts -->
      <Card
        class="border border-[var(--silver-200)] dark:border-[var(--silver-300)]/60 bg-card rounded-none gap-0 py-0 lg:col-span-2"
      >
        <CardHeader
          class="flex flex-row items-center gap-2 px-5 py-4 bg-[var(--silver-50)] dark:bg-[var(--silver-100)]/10 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]/50"
        >
          <IconKeyboard class="size-4 text-[var(--accent-primary)]" />
          <CardTitle class="text-sm font-bold font-mono uppercase tracking-wide text-foreground">
            {{ t('help.shortcuts.title') }}
          </CardTitle>
        </CardHeader>
        <CardContent class="px-5 py-4">
          <ul class="divide-y divide-[var(--silver-200)] dark:divide-[var(--silver-300)]/40">
            <li
              v-for="sc in shortcuts"
              :key="sc.descriptionKey"
              class="flex items-center justify-between gap-3 py-2.5"
            >
              <span class="text-sm text-foreground">{{ t(sc.descriptionKey) }}</span>
              <span class="flex items-center gap-1">
                <kbd
                  v-for="key in sc.keys"
                  :key="key"
                  class="inline-flex h-6 min-w-[1.75rem] items-center justify-center px-1.5 font-mono text-xs font-medium text-foreground bg-[var(--surface-sunken)] border border-[var(--border)] rounded-none"
                >
                  {{ key }}
                </kbd>
              </span>
            </li>
          </ul>
        </CardContent>
      </Card>

      <!-- System info + quick links -->
      <div class="flex flex-col gap-5">
        <Card
          class="border border-[var(--silver-200)] dark:border-[var(--silver-300)]/60 bg-card rounded-none gap-0 py-0"
        >
          <CardHeader
            class="flex flex-row items-center gap-2 px-5 py-4 bg-[var(--silver-50)] dark:bg-[var(--silver-100)]/10 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]/50"
          >
            <IconBolt class="size-4 text-[var(--accent-primary)]" />
            <CardTitle class="text-sm font-bold font-mono uppercase tracking-wide text-foreground">
              {{ t('help.system.title') }}
            </CardTitle>
          </CardHeader>
          <CardContent class="px-5 py-4 space-y-2 font-mono text-xs">
            <div class="flex items-center justify-between">
              <span class="text-[var(--silver-500)]">{{ t('help.system.version') }}</span>
              <span class="text-foreground tabular-nums">{{ appVersion }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[var(--silver-500)]">{{ t('help.system.builtAt') }}</span>
              <span class="text-foreground tabular-nums">{{ buildTime }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[var(--silver-500)]">{{ t('help.system.user') }}</span>
              <span class="text-foreground">{{ authStore.userName }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[var(--silver-500)]">{{ t('help.system.role') }}</span>
              <span class="text-foreground uppercase">{{ authStore.userRole || '—' }}</span>
            </div>
          </CardContent>
        </Card>

        <Card
          class="border border-[var(--silver-200)] dark:border-[var(--silver-300)]/60 bg-card rounded-none gap-0 py-0"
        >
          <CardHeader
            class="flex flex-row items-center gap-2 px-5 py-4 bg-[var(--silver-50)] dark:bg-[var(--silver-100)]/10 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]/50"
          >
            <IconBolt class="size-4 text-[var(--accent-primary)]" />
            <CardTitle class="text-sm font-bold font-mono uppercase tracking-wide text-foreground">
              {{ t('help.quickLinks.title') }}
            </CardTitle>
          </CardHeader>
          <CardContent class="px-3 py-3 flex flex-col gap-1">
            <Button
              v-for="link in quickLinks"
              :key="link.to"
              variant="ghost"
              class="h-8 justify-start gap-2 px-2 rounded-none text-foreground hover:bg-[var(--surface-sunken)]"
              @click="go(link.to)"
            >
              <component :is="link.icon" class="size-4 text-[var(--silver-500)]" />
              <span class="text-sm">{{ t(link.labelKey) }}</span>
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>

    <!-- Docs link -->
    <Card
      class="border border-[var(--silver-200)] dark:border-[var(--silver-300)]/60 bg-card rounded-none gap-0 py-0"
    >
      <CardContent
        class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 px-5 py-4"
      >
        <div class="flex items-start gap-3">
          <IconBook class="size-5 mt-0.5 text-[var(--accent-primary)] shrink-0" />
          <div>
            <CardTitle class="text-sm font-semibold text-foreground">
              {{ t('help.docs.title') }}
            </CardTitle>
            <CardDescription class="text-xs text-[var(--silver-500)] mt-1">
              {{ t('help.docs.description') }}
            </CardDescription>
          </div>
        </div>
        <a
          href="/docs/"
          target="_blank"
          rel="noopener"
          class="inline-flex items-center gap-1.5 px-3 h-8 text-xs font-mono font-bold uppercase tracking-wide border border-[var(--silver-300)] text-foreground bg-transparent hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] transition-colors duration-200 rounded-none"
        >
          <span>{{ t('help.docs.openFolder') }}</span>
          <IconExternalLink class="size-3.5" />
        </a>
      </CardContent>
    </Card>
  </div>
</template>
