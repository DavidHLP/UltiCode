<script setup lang="ts">
import { forumPermissionRules } from '@/composables/domainPermissionMaps'
import { usePermissionMap } from '@/composables/usePermissionMap'
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useForumStore } from '@/stores/admin/forum'
import { Button } from '@ulticode/design-system/ui/button'
import { Skeleton } from '@ulticode/design-system/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@ulticode/design-system/ui/tabs'
import StatusBadge from '@ulticode/design-system/StatusBadge.vue'
import {
  ArrowLeft as IconArrowLeft,
  Flag as IconFlag,
  Lock as IconLock,
  Pin as IconPin,
  Trash as IconTrash,
  FileText as IconFileText,
  MessageSquare as IconMessage,
  History as IconHistory,
} from '@lucide/vue'

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
const can = { forum: usePermissionMap(forumPermissionRules) }

const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)
const auditLoading = ref(false)

const postId = computed(() => route.params.id as string)
const post = computed(() => forumStore.currentPost)
const canModerate = can.forum.moderatePost
const canDelete = can.forum.deletePost

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
    <!-- Page header -->
    <header
      :class="[
        'sticky top-0 z-10 bg-card border-b border-border dark:border-border',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <!-- Left: Back & Title -->
        <div class="flex items-center gap-4 min-w-0">
          <Button
            variant="outline"
            size="sm"
            class="w-8 p-0"
            @click="router.push({ name: 'forum-posts' })"
          >
            <IconArrowLeft class="h-4 w-4" />
          </Button>

          <div v-if="post" class="flex items-center gap-3 min-w-0">
            <div class="flex items-center gap-2"></div>
            <h1 class="text-base font-medium text-foreground truncate">
              {{ post.title }}
            </h1>
          </div>
          <Skeleton v-else class="h-5 w-32" />
        </div>

        <!-- Center: Tabs (Desktop) -->
        <div class="absolute left-1/2 -translate-x-1/2 hidden md:block">
          <Tabs :model-value="currentView" @update:model-value="handleTabChange">
            <TabsList class="h-9">
              <TabsTrigger value="overview" class="h-7 px-3">
                <span class="text-muted-foreground mr-1">01</span>
                <IconFileText :size="14" class="mr-1" />
                {{ t('forum.tabs.overview') }}
              </TabsTrigger>
              <TabsTrigger value="comments" class="h-7 px-3">
                <span class="text-muted-foreground mr-1">02</span>
                <IconMessage :size="14" class="mr-1" />
                {{ t('forum.tabs.comments') }}
              </TabsTrigger>
              <TabsTrigger value="audit" class="h-7 px-3">
                <span class="text-muted-foreground mr-1">03</span>
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
            <StatusBadge v-if="post.isPinned" variant="info" :label="t('forum.status.pinned')" />
            <StatusBadge v-if="post.isLocked" variant="warning" :label="t('forum.status.locked')" />
            <StatusBadge
              v-if="post.isFlagged"
              variant="error"
              pulse
              :label="t('forum.status.flagged')"
            />
            <StatusBadge v-if="post.isDeleted" variant="error" :label="t('forum.status.deleted')" />
          </div>

          <!-- Action Buttons -->
          <template v-if="canModerate">
            <Button variant="outline" size="sm" @click="togglePin">
              <IconPin class="h-3.5 w-3.5 mr-1.5" />
              <span class="uppercase tracking-wider">{{
                post.isPinned ? t('forum.actions.unpin') : t('forum.actions.pin')
              }}</span>
            </Button>

            <Button variant="outline" size="sm" @click="toggleLock">
              <IconLock class="h-3.5 w-3.5 mr-1.5" />
              <span class="uppercase tracking-wider">{{
                post.isLocked ? t('forum.actions.unlock') : t('forum.actions.lock')
              }}</span>
            </Button>

            <Button v-if="post.isFlagged" variant="outline" size="sm" @click="unflagPost">
              <IconFlag class="h-3.5 w-3.5 mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('forum.actions.unflag') }}</span>
            </Button>
            <Button v-else variant="outline" size="sm" @click="flagDialogOpen = true">
              <IconFlag class="h-3.5 w-3.5 mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('forum.actions.flag') }}</span>
            </Button>
          </template>

          <Button
            v-if="canDelete"
            variant="outline"
            size="sm"
            class="w-8 p-0"
            @click="deleteDialogOpen = true"
          >
            <IconTrash class="h-4 w-4" />
          </Button>
        </div>
      </div>

      <!-- Mobile Tabs (Below Header) -->
      <div class="md:hidden border-t border-border dark:border-border p-1 bg-muted">
        <Tabs :model-value="currentView" @update:model-value="handleTabChange" class="w-full">
          <TabsList class="w-full h-9">
            <TabsTrigger value="overview" class="flex-1 h-7">
              <span class="text-muted-foreground mr-1">01</span>
              {{ t('forum.tabs.overview') }}
            </TabsTrigger>
            <TabsTrigger value="comments" class="flex-1 h-7">
              <span class="text-muted-foreground mr-1">02</span>
              {{ t('forum.tabs.comments') }}
            </TabsTrigger>
            <TabsTrigger value="audit" class="flex-1 h-7">
              <span class="text-muted-foreground mr-1">03</span>
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
          class="w-12 h-12 border border-status-error-mark flex items-center justify-center mb-3"
        >
          <IconFileText :size="24" class="text-status-error-mark" />
        </div>
        <h2 class="text-sm font-semibold mb-1 font-mono">{{ t('forum.error.loadingPost') }}</h2>
        <p class="text-xs text-muted-foreground mb-4 font-mono">
          {{ forumStore.postError }}
        </p>
        <div class="flex gap-2">
          <Button variant="outline" size="sm" @click="router.push({ name: 'forum-posts' })">
            {{ t('forum.error.back') }}
          </Button>
          <Button variant="outline" size="sm" @click="refresh">
            {{ t('forum.error.retry') }}
          </Button>
        </div>
      </div>

      <!-- Loading State -->
      <div v-else-if="isInitialLoad || forumStore.postLoading" class="space-y-6">
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div class="lg:col-span-8 space-y-4">
            <Skeleton class="h-12 w-3/4" />
            <Skeleton class="h-64 w-full" />
          </div>
          <div class="lg:col-span-4 space-y-4">
            <Skeleton class="h-32 w-full" />
            <Skeleton class="h-32 w-full" />
          </div>
        </div>
      </div>

      <!-- Not Found State -->
      <div v-else-if="!post" class="flex flex-col items-center justify-center py-24 text-center">
        <div class="w-12 h-12 border border-border flex items-center justify-center mb-3">
          <IconFileText :size="24" class="text-muted-foreground" />
        </div>
        <h2 class="text-sm font-semibold mb-1 font-mono">{{ t('forum.error.postNotFound') }}</h2>
        <p class="text-xs text-muted-foreground mb-4">
          {{ t('forum.error.notFoundDescription') }}
        </p>
        <Button variant="outline" size="sm" @click="router.push({ name: 'forum-posts' })">
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
