<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useForumStore } from '@/stores/admin/forum'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { TerminalBadge } from '@/components/ui/terminal'
import {
  IconArrowLeft,
  IconFlag,
  IconLock,
  IconPin,
  IconTrash,
  IconFileText,
  IconMessage,
  IconHistory,
} from '@tabler/icons-vue'
import { toast } from 'vue-sonner'
import OverviewDisplay from './components/OverviewDisplay.vue'
import CommentsTab from './components/CommentsTab.vue'
import AuditTab from './components/AuditTab.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { useDetailWorkspace } from '@/composables/useDetailWorkspace'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const forumStore = useForumStore()
const authStore = useAuthStore()

const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)
const auditLoading = ref(false)

const postId = computed(() => route.params.id as string)
const post = computed(() => forumStore.currentPost)
const canModerate = computed(() => authStore.hasPermission('MODERATE', 'FORUM_POST'))
const canDelete = computed(() => authStore.hasPermission('DELETE', 'FORUM_POST'))

// Detail lifecycle (first-load skeleton, mount animation, refresh) lives in
// the shared workspace; audit history is the secondary refresh.
const { isInitialLoad, isLoaded, refresh } = useDetailWorkspace({
  entityId: postId,
  fetch: (id) => forumStore.fetchPostDetail(id),
  onRefreshed: () => loadAuditHistory(),
})

// Determine current view from route
const currentView = computed(() => {
  const path = route.path
  if (path.endsWith('/comments')) return 'comments'
  if (path.endsWith('/audit')) return 'audit'
  return 'overview'
})

function handleTabChange(value: string | number) {
  const view = value as string
  router.push({ name: 'forum-post-detail', params: { id: postId.value, tab: view } })
}

async function loadAuditHistory() {
  auditLoading.value = true
  try {
    await forumStore.fetchPostAuditHistory(postId.value)
  } catch {
    // Silently fail for audit history
  } finally {
    auditLoading.value = false
  }
}

async function togglePin() {
  if (!post.value) return
  try {
    await forumStore.togglePin(post.value)
    toast.success(
      post.value.isPinned
        ? t('forum.toast.unpinnedSuccessfully')
        : t('forum.toast.pinnedSuccessfully'),
    )
    await refresh() // reload detail (+ audit via onRefreshed)
  } catch {
    toast.error(t('forum.toast.failedToUpdatePin'))
  }
}

async function toggleLock() {
  if (!post.value) return
  try {
    await forumStore.toggleLock(post.value)
    toast.success(
      post.value.isLocked
        ? t('forum.toast.unlockedSuccessfully')
        : t('forum.toast.lockedSuccessfully'),
    )
    await refresh() // reload detail (+ audit via onRefreshed)
  } catch {
    toast.error(t('forum.toast.failedToUpdateLock'))
  }
}

async function unflagPost() {
  if (!post.value) return
  try {
    await forumStore.unflagPost(postId.value)
    toast.success(t('forum.toast.unflaggedSuccessfully'))
    await refresh() // refresh already reloads audit via onRefreshed
  } catch {
    toast.error(t('forum.toast.failedToUnflag'))
  }
}

function handleDeleteSuccess() {
  router.push({ name: 'forum-posts' })
}

function handleFlagSuccess() {
  refresh()
}

async function handleDeletePost(id: string | number) {
  await forumStore.deletePost(String(id))
}

async function handleFlagPost(id: string | number, reason?: string) {
  await forumStore.flagPost(String(id), reason || '')
}
</script>

<template>
  <div class="min-h-[calc(100vh-4rem)] bg-background flex flex-col">
    <!-- Terminal Header -->
    <header
      :class="[
        'sticky top-0 z-10 bg-[var(--card)] border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <!-- Left: Back & Title -->
        <div class="flex items-center gap-4 min-w-0">
          <Button
            variant="terminal"
            size="sm"
            class="h-8 w-8 p-0 border-[var(--silver-300)] hover:border-[var(--silver-400)]"
            @click="router.push({ name: 'forum-posts' })"
          >
            <IconArrowLeft class="h-4 w-4" />
          </Button>

          <div v-if="post" class="flex items-center gap-3 min-w-0">
            <div class="flex items-center gap-2"></div>
            <h1 class="text-base font-medium text-[var(--foreground)] truncate">
              {{ post.title }}
            </h1>
          </div>
          <Skeleton v-else class="h-5 w-32" />
        </div>

        <!-- Center: Tabs (Desktop) -->
        <div class="absolute left-1/2 -translate-x-1/2 hidden md:block">
          <Tabs :model-value="currentView" @update:model-value="handleTabChange">
            <TabsList class="h-9 border border-[var(--silver-200)] dark:border-[var(--silver-700)]">
              <TabsTrigger
                value="overview"
                class="text-xs h-7 px-3 font-data data-[state=active]:bg-[var(--surface-sunken)]"
              >
                <span class="text-[var(--silver-500)] mr-1">01</span>
                <IconFileText :size="14" class="mr-1" />
                {{ t('forum.tabs.overview') }}
              </TabsTrigger>
              <TabsTrigger
                value="comments"
                class="text-xs h-7 px-3 font-data data-[state=active]:bg-[var(--surface-sunken)]"
              >
                <span class="text-[var(--silver-500)] mr-1">02</span>
                <IconMessage :size="14" class="mr-1" />
                {{ t('forum.tabs.comments') }}
              </TabsTrigger>
              <TabsTrigger
                value="audit"
                class="text-xs h-7 px-3 font-data data-[state=active]:bg-[var(--surface-sunken)]"
              >
                <span class="text-[var(--silver-500)] mr-1">03</span>
                <IconHistory :size="14" class="mr-1" />
                {{ t('forum.tabs.audit') }}
              </TabsTrigger>
            </TabsList>
          </Tabs>
        </div>

        <!-- Right: Actions & Status -->
        <div v-if="post" class="flex items-center gap-3">
          <!-- Status Badges -->
          <div class="hidden sm:flex items-center gap-2">
            <TerminalBadge v-if="post.isPinned" variant="info" :label="t('forum.status.pinned')" />
            <TerminalBadge
              v-if="post.isLocked"
              variant="warning"
              :label="t('forum.status.locked')"
            />
            <TerminalBadge
              v-if="post.isFlagged"
              variant="error"
              pulse
              :label="t('forum.status.flagged')"
            />
            <TerminalBadge
              v-if="post.isDeleted"
              variant="error"
              :label="t('forum.status.deleted')"
            />
          </div>

          <!-- Action Buttons -->
          <template v-if="canModerate">
            <Button
              variant="terminal"
              size="sm"
              class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-cyan)] hover:text-[var(--terminal-cyan)]"
              @click="togglePin"
            >
              <IconPin class="h-3.5 w-3.5 mr-1.5" />
              <span class="uppercase tracking-wider">{{
                post.isPinned ? t('forum.actions.unpin') : t('forum.actions.pin')
              }}</span>
            </Button>

            <Button
              variant="terminal"
              size="sm"
              class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-amber)] hover:text-[var(--terminal-amber)]"
              @click="toggleLock"
            >
              <IconLock class="h-3.5 w-3.5 mr-1.5" />
              <span class="uppercase tracking-wider">{{
                post.isLocked ? t('forum.actions.unlock') : t('forum.actions.lock')
              }}</span>
            </Button>

            <Button
              v-if="post.isFlagged"
              variant="terminal"
              size="sm"
              class="h-8 font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[color-mix(in_oklch,_var(--terminal-green)_10%,_transparent)]"
              @click="unflagPost"
            >
              <IconFlag class="h-3.5 w-3.5 mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('forum.actions.unflag') }}</span>
            </Button>
            <Button
              v-else
              variant="terminal"
              size="sm"
              class="h-8 font-data text-xs border-[var(--terminal-amber)] text-[var(--terminal-amber)] hover:bg-[color-mix(in_oklch,_var(--terminal-amber)_10%,_transparent)]"
              @click="flagDialogOpen = true"
            >
              <IconFlag class="h-3.5 w-3.5 mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('forum.actions.flag') }}</span>
            </Button>
          </template>

          <Button
            v-if="canDelete"
            variant="terminal"
            size="sm"
            class="h-8 w-8 p-0 border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]"
            @click="deleteDialogOpen = true"
          >
            <IconTrash class="h-4 w-4" />
          </Button>
        </div>
      </div>

      <!-- Mobile Tabs (Below Header) -->
      <div
        class="md:hidden border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] p-1 bg-[var(--surface-sunken)]"
      >
        <Tabs :model-value="currentView" @update:model-value="handleTabChange" class="w-full">
          <TabsList
            class="w-full h-9 border border-[var(--silver-200)] dark:border-[var(--silver-700)]"
          >
            <TabsTrigger
              value="overview"
              class="flex-1 text-xs h-7 font-data data-[state=active]:bg-[var(--card)]"
            >
              <span class="text-[var(--silver-500)] mr-1">01</span>
              {{ t('forum.tabs.overview') }}
            </TabsTrigger>
            <TabsTrigger
              value="comments"
              class="flex-1 text-xs h-7 font-data data-[state=active]:bg-[var(--card)]"
            >
              <span class="text-[var(--silver-500)] mr-1">02</span>
              {{ t('forum.tabs.comments') }}
            </TabsTrigger>
            <TabsTrigger
              value="audit"
              class="flex-1 text-xs h-7 font-data data-[state=active]:bg-[var(--card)]"
            >
              <span class="text-[var(--silver-500)] mr-1">03</span>
              {{ t('forum.tabs.audit') }}
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 w-full max-w-[1600px] mx-auto p-4 lg:p-6 lg:pt-8">
      <!-- Error State -->
      <div
        v-if="forumStore.postError"
        class="flex flex-col items-center justify-center py-24 text-center"
      >
        <div
          class="w-12 h-12 border border-[var(--terminal-red)] flex items-center justify-center mb-3"
        >
          <IconFileText :size="24" class="text-[var(--terminal-red)]" />
        </div>
        <h2 class="text-sm font-semibold mb-1 font-data">{{ t('forum.error.loadingPost') }}</h2>
        <p class="text-xs text-[var(--silver-400)] mb-4 font-data">{{ forumStore.postError }}</p>
        <div class="flex gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)]"
            @click="router.push({ name: 'forum-posts' })"
          >
            {{ t('forum.error.back') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--accent-electric)] text-[var(--accent-electric)]"
            @click="refresh"
          >
            {{ t('forum.error.retry') }}
          </Button>
        </div>
      </div>

      <!-- Loading State -->
      <div v-else-if="isInitialLoad || forumStore.postLoading" class="space-y-6">
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div class="lg:col-span-8 space-y-4">
            <Skeleton class="h-12 w-3/4 rounded-none" />
            <Skeleton class="h-64 w-full rounded-none" />
          </div>
          <div class="lg:col-span-4 space-y-4">
            <Skeleton class="h-32 w-full rounded-none" />
            <Skeleton class="h-32 w-full rounded-none" />
          </div>
        </div>
      </div>

      <!-- Not Found State -->
      <div v-else-if="!post" class="flex flex-col items-center justify-center py-24 text-center">
        <div
          class="w-12 h-12 border border-[var(--silver-300)] flex items-center justify-center mb-3"
        >
          <IconFileText :size="24" class="text-[var(--silver-400)]" />
        </div>
        <h2 class="text-sm font-semibold mb-1 font-data">{{ t('forum.error.postNotFound') }}</h2>
        <p class="text-xs text-[var(--silver-400)] mb-4">
          {{ t('forum.error.notFoundDescription') }}
        </p>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)]"
          @click="router.push({ name: 'forum-posts' })"
        >
          {{ t('forum.error.backToForumPosts') }}
        </Button>
      </div>

      <!-- Post Content -->
      <template v-else>
        <transition
          mode="out-in"
          enter-active-class="transition-opacity duration-200 ease-in-out"
          enter-from-class="opacity-0"
          enter-to-class="opacity-100"
          leave-active-class="transition-opacity duration-150 ease-in-out"
          leave-from-class="opacity-100"
          leave-to-class="opacity-0"
        >
          <component
            :is="
              currentView === 'overview'
                ? OverviewDisplay
                : currentView === 'comments'
                  ? CommentsTab
                  : AuditTab
            "
            :key="post ? `${currentView}-${post.id}` : currentView"
            :post="post"
            :post-id="postId"
            :audit-history="forumStore.auditHistory"
            :loading="auditLoading"
          />
        </transition>
      </template>
    </main>

    <!-- Dialogs -->
    <EntityActionDialog
      v-model:open="deleteDialogOpen"
      :entity-id="postId"
      :entity-title="post?.title || null"
      action="delete"
      :title="t('forum.delete.title')"
      :description="t('forum.delete.description')"
      :confirm-label="t('forum.delete.confirm')"
      :cancel-label="t('forum.delete.cancel')"
      :success-label="t('forum.toast.deletedSuccessfully')"
      :error-label="t('forum.toast.failedToDelete')"
      :on-action="handleDeletePost"
      @success="handleDeleteSuccess"
    />

    <EntityActionDialog
      v-model:open="flagDialogOpen"
      :entity-id="postId"
      action="flag"
      :title="t('forum.flag.title')"
      :description="t('forum.flag.description')"
      :confirm-label="t('forum.flag.confirm')"
      :cancel-label="t('forum.flag.cancel')"
      :success-label="t('forum.toast.flaggedSuccessfully')"
      :error-label="t('forum.toast.failedToFlag')"
      :reason-label="t('forum.flag.reasonLabel')"
      :reason-placeholder="t('forum.flag.reasonPlaceholder')"
      :reason-required-label="t('forum.toast.reasonRequired')"
      :on-action="handleFlagPost"
      @success="handleFlagSuccess"
    />
  </div>
</template>
