<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useForumStore } from '@/stores/admin/forum'
import { useAuthStore } from '@/stores/admin/auth'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  ArrowLeft,
  Flag,
  Lock,
  Pin,
  Trash,
  FileText,
  MessageSquare,
  History,
} from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import OverviewDisplay from './components/OverviewDisplay.vue'
import CommentsTab from './components/CommentsTab.vue'
import AuditTab from './components/AuditTab.vue'
import ForumPostDeleteDialog from './ForumPostDeleteDialog.vue'
import ForumPostFlagDialog from './ForumPostFlagDialog.vue'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const forumStore = useForumStore()
const authStore = useAuthStore()

const isInitialLoad = ref(true)
const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)
const auditLoading = ref(false)

const postId = computed(() => route.params.id as string)
const post = computed(() => forumStore.currentPost)
const canModerate = computed(() => authStore.hasPermission('MODERATE', 'FORUM_POST'))
const canDelete = computed(() => authStore.hasPermission('DELETE', 'FORUM_POST'))

// Determine current view from route
const currentView = computed(() => {
  const path = route.path
  if (path.endsWith('/comments')) return 'comments'
  if (path.endsWith('/audit')) return 'audit'
  return 'overview'
})

function handleTabChange(value: string | number) {
  const view = value as string
  const routeName = `forum-post-detail-${view}`
  router.push({ name: routeName, params: { id: postId.value } })
}

onMounted(async () => {
  if (postId.value) {
    await loadData()
    isInitialLoad.value = false
  }
})

async function loadData() {
  try {
    await forumStore.fetchPostDetail(postId.value)
    // Load audit history in background
    loadAuditHistory()
  } catch {
    // Error is handled in store
  }
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
      post.value.is_pinned
        ? t('forum.toast.unpinnedSuccessfully')
        : t('forum.toast.pinnedSuccessfully'),
    )
    await loadData() // Reload to get fresh data
  } catch {
    toast.error(t('forum.toast.failedToUpdatePin'))
  }
}

async function toggleLock() {
  if (!post.value) return
  try {
    await forumStore.toggleLock(post.value)
    toast.success(
      post.value.is_locked
        ? t('forum.toast.unlockedSuccessfully')
        : t('forum.toast.lockedSuccessfully'),
    )
    await loadData() // Reload to get fresh data
  } catch {
    toast.error(t('forum.toast.failedToUpdateLock'))
  }
}

async function unflagPost() {
  if (!post.value) return
  try {
    await forumStore.unflagPost(postId.value)
    toast.success(t('forum.toast.unflaggedSuccessfully'))
    await loadData() // Reload to get fresh data
    await loadAuditHistory() // Refresh audit history
  } catch {
    toast.error(t('forum.toast.failedToUnflag'))
  }
}

function handleDeleteSuccess() {
  router.push({ name: 'forum-posts' })
}

function handleFlagSuccess() {
  loadData()
  loadAuditHistory()
}
</script>

<template>
  <div class="min-h-[calc(100vh-4rem)] bg-background flex flex-col">
    <!-- Header -->
    <header class="sticky top-0 z-10 bg-background/95 backdrop-blur border-b">
      <div class="flex items-center justify-between h-14 px-4 lg:px-6">
        <!-- Left: Back & Title -->
        <div class="flex items-center gap-4 min-w-0">
          <Button
            variant="ghost"
            size="icon"
            class="h-8 w-8 text-muted-foreground -ml-2"
            @click="router.push({ name: 'forum-posts' })"
          >
            <ArrowLeft :size="18" />
          </Button>

          <div v-if="post" class="flex items-center gap-3 min-w-0">
            <h1 class="text-sm font-semibold truncate">{{ post.title }}</h1>
            <div class="hidden sm:flex items-center gap-2">
              <Badge v-if="post.is_pinned" variant="default" class="text-[10px] px-1.5 py-0 h-5">
                {{ t('forum.status.pinned') }}
              </Badge>
              <Badge v-if="post.is_locked" variant="secondary" class="text-[10px] px-1.5 py-0 h-5">
                {{ t('forum.status.locked') }}
              </Badge>
              <Badge
                v-if="post.is_flagged"
                variant="destructive"
                class="text-[10px] px-1.5 py-0 h-5"
              >
                {{ t('forum.status.flagged') }}
              </Badge>
              <Badge
                v-if="post.is_deleted"
                variant="destructive"
                class="text-[10px] px-1.5 py-0 h-5"
              >
                {{ t('forum.status.deleted') }}
              </Badge>
            </div>
          </div>
          <Skeleton v-else class="h-5 w-32" />
        </div>

        <!-- Center: Tabs (Desktop) -->
        <div class="absolute left-1/2 -translate-x-1/2 hidden md:block">
          <Tabs :model-value="currentView" @update:model-value="handleTabChange">
            <TabsList class="h-9">
              <TabsTrigger value="overview" class="text-xs h-7 px-3">
                <FileText :size="14" class="mr-1" />
                {{ t('forum.tabs.overview') }}
              </TabsTrigger>
              <TabsTrigger value="comments" class="text-xs h-7 px-3">
                <MessageSquare :size="14" class="mr-1" />
                {{ t('forum.tabs.comments') }}
              </TabsTrigger>
              <TabsTrigger value="audit" class="text-xs h-7 px-3">
                <History :size="14" class="mr-1" />
                {{ t('forum.tabs.audit') }}
              </TabsTrigger>
            </TabsList>
          </Tabs>
        </div>

        <!-- Right: Actions -->
        <div v-if="post" class="flex items-center gap-2">
          <template v-if="canModerate">
            <Button
              variant="outline"
              size="sm"
              class="h-8 gap-1.5 hidden sm:flex"
              @click="togglePin"
            >
              <Pin :size="14" />
              <span>{{ post.is_pinned ? t('forum.actions.unpin') : t('forum.actions.pin') }}</span>
            </Button>

            <Button
              variant="outline"
              size="sm"
              class="h-8 gap-1.5 hidden sm:flex"
              @click="toggleLock"
            >
              <Lock :size="14" />
              <span>{{
                post.is_locked ? t('forum.actions.unlock') : t('forum.actions.lock')
              }}</span>
            </Button>

            <Button
              v-if="post.is_flagged"
              variant="outline"
              size="sm"
              class="h-8 gap-1.5 hidden sm:flex text-emerald-600 hover:text-emerald-700"
              @click="unflagPost"
            >
              <Flag :size="14" />
              <span>{{ t('forum.actions.unflag') }}</span>
            </Button>
            <Button
              v-else
              variant="outline"
              size="sm"
              class="h-8 gap-1.5 hidden sm:flex text-amber-600 hover:text-amber-700"
              @click="flagDialogOpen = true"
            >
              <Flag :size="14" />
              <span>{{ t('forum.actions.flag') }}</span>
            </Button>
          </template>

          <Button
            v-if="canDelete"
            variant="ghost"
            size="icon"
            class="h-8 w-8 text-destructive hover:text-destructive hover:bg-destructive/10"
            @click="deleteDialogOpen = true"
          >
            <Trash :size="16" />
          </Button>
        </div>
      </div>

      <!-- Mobile Tabs (Below Header) -->
      <div class="md:hidden border-t p-1 bg-muted/10">
        <Tabs :model-value="currentView" @update:model-value="handleTabChange" class="w-full">
          <TabsList class="w-full h-9">
            <TabsTrigger value="overview" class="flex-1 text-xs h-7">
              <FileText :size="14" class="mr-1" />
              {{ t('forum.tabs.overview') }}
            </TabsTrigger>
            <TabsTrigger value="comments" class="flex-1 text-xs h-7">
              <MessageSquare :size="14" class="mr-1" />
              {{ t('forum.tabs.comments') }}
            </TabsTrigger>
            <TabsTrigger value="audit" class="flex-1 text-xs h-7">
              <History :size="14" class="mr-1" />
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
        <div class="w-12 h-12 rounded-full bg-muted flex items-center justify-center mb-3">
          <FileText :size="24" class="text-muted-foreground" />
        </div>
        <h2 class="text-sm font-semibold mb-1">{{ t('forum.error.loadingPost') }}</h2>
        <p class="text-xs text-muted-foreground mb-4">{{ forumStore.postError }}</p>
        <div class="flex gap-2">
          <Button variant="outline" size="sm" @click="router.push({ name: 'forum-posts' })">
            {{ t('forum.error.back') }}
          </Button>
          <Button size="sm" @click="loadData">{{ t('forum.error.retry') }}</Button>
        </div>
      </div>

      <!-- Loading State -->
      <div v-else-if="isInitialLoad || forumStore.postLoading" class="space-y-6">
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div class="lg:col-span-8 space-y-4">
            <Skeleton class="h-12 w-3/4 rounded-lg" />
            <Skeleton class="h-64 w-full rounded-xl" />
          </div>
          <div class="lg:col-span-4 space-y-4">
            <Skeleton class="h-32 w-full rounded-xl" />
            <Skeleton class="h-32 w-full rounded-xl" />
          </div>
        </div>
      </div>

      <!-- Not Found State -->
      <div v-else-if="!post" class="flex flex-col items-center justify-center py-24 text-center">
        <div class="w-12 h-12 rounded-full bg-muted flex items-center justify-center mb-3">
          <FileText :size="24" class="text-muted-foreground" />
        </div>
        <h2 class="text-sm font-semibold mb-1">{{ t('forum.error.postNotFound') }}</h2>
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
    <ForumPostDeleteDialog
      v-if="post"
      v-model:open="deleteDialogOpen"
      :post-id="post.id"
      @success="handleDeleteSuccess"
    />

    <ForumPostFlagDialog
      v-if="post"
      v-model:open="flagDialogOpen"
      :post-id="post.id"
      @success="handleFlagSuccess"
    />
  </div>
</template>
