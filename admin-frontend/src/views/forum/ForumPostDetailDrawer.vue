<script setup lang="ts">
import { computed } from 'vue'
import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
} from '@/components/ui/drawer'
import { Badge } from '@/components/ui/badge'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import {
  IconCalendar,
  IconEye,
  IconFlag,
  IconLock,
  IconMessage,
  IconMessageCircle,
  IconPin,
  IconThumbDown,
  IconThumbUp,
  IconTrash,
} from '@tabler/icons-vue'
import type { ForumPost } from '@/api/admin/forum'

const props = defineProps<{
  open: boolean
  post: ForumPost | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const authorInitials = computed(() => {
  if (!props.post?.author?.username) return '?'
  return props.post.author.username.slice(0, 2).toUpperCase()
})

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleString()
}
</script>

<template>
  <Drawer :open="open" @update:open="emit('update:open', $event)" direction="right">
    <DrawerContent class="h-full w-[400px] sm:w-[540px]">
      <DrawerHeader class="border-b px-6 py-4">
        <div class="flex items-center justify-between">
          <div>
            <DrawerTitle>Post Details</DrawerTitle>
            <DrawerDescription>View forum post information and content.</DrawerDescription>
          </div>
        </div>
      </DrawerHeader>

      <ScrollArea v-if="post" class="flex-1">
        <div class="flex flex-col gap-6 p-6">
          <!-- Post Header -->
          <div class="space-y-3">
            <div class="flex items-start gap-3">
              <div
                class="h-12 w-12 rounded-xl bg-primary/10 flex items-center justify-center text-primary"
              >
                <IconMessageCircle class="h-6 w-6" />
              </div>
              <div class="flex-1 space-y-1">
                <h3 class="text-lg font-semibold leading-tight">{{ post.title }}</h3>
                <div class="flex flex-wrap gap-2">
                  <Badge v-if="post.is_pinned" variant="default">
                    <IconPin class="h-3 w-3 mr-1" />
                    Pinned
                  </Badge>
                  <Badge v-if="post.is_locked" variant="secondary">
                    <IconLock class="h-3 w-3 mr-1" />
                    Locked
                  </Badge>
                  <Badge v-if="post.is_flagged" variant="destructive">
                    <IconFlag class="h-3 w-3 mr-1" />
                    Flagged
                  </Badge>
                  <Badge v-if="post.is_deleted" variant="destructive">
                    <IconTrash class="h-3 w-3 mr-1" />
                    Deleted
                  </Badge>
                  <Badge
                    v-if="
                      !post.is_pinned && !post.is_locked && !post.is_flagged && !post.is_deleted
                    "
                    variant="outline"
                  >
                    Active
                  </Badge>
                </div>
              </div>
            </div>
          </div>

          <Separator />

          <!-- Author & Community -->
          <div class="space-y-4">
            <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
              Author & Community
            </h4>
            <div class="flex items-center gap-3">
              <Avatar class="h-10 w-10">
                <AvatarImage :src="post.author?.avatar || ''" :alt="post.author?.username" />
                <AvatarFallback>{{ authorInitials }}</AvatarFallback>
              </Avatar>
              <div class="flex flex-col">
                <span class="font-medium text-sm">{{ post.author?.username || 'Unknown' }}</span>
                <span class="text-xs text-muted-foreground">
                  in {{ post.community?.name || 'Unknown Community' }}
                </span>
              </div>
            </div>
          </div>

          <Separator />

          <!-- Statistics -->
          <div class="space-y-4">
            <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
              Statistics
            </h4>
            <div class="grid grid-cols-2 gap-4">
              <div
                class="rounded-lg border bg-card p-4 flex flex-col items-center justify-center text-center"
              >
                <IconEye class="h-6 w-6 text-blue-500 mb-2" />
                <span class="text-2xl font-bold tabular-nums">{{ post.view_count || 0 }}</span>
                <span class="text-xs text-muted-foreground uppercase">Views</span>
              </div>
              <div
                class="rounded-lg border bg-card p-4 flex flex-col items-center justify-center text-center"
              >
                <IconMessage class="h-6 w-6 text-purple-500 mb-2" />
                <span class="text-2xl font-bold tabular-nums">{{ post.comment_count || 0 }}</span>
                <span class="text-xs text-muted-foreground uppercase">Comments</span>
              </div>
              <div
                class="rounded-lg border bg-card p-4 flex flex-col items-center justify-center text-center"
              >
                <IconThumbUp class="h-6 w-6 text-emerald-500 mb-2" />
                <span class="text-2xl font-bold tabular-nums">{{ post.upvotes || 0 }}</span>
                <span class="text-xs text-muted-foreground uppercase">Upvotes</span>
              </div>
              <div
                class="rounded-lg border bg-card p-4 flex flex-col items-center justify-center text-center"
              >
                <IconThumbDown class="h-6 w-6 text-rose-500 mb-2" />
                <span class="text-2xl font-bold tabular-nums">{{ post.downvotes || 0 }}</span>
                <span class="text-xs text-muted-foreground uppercase">Downvotes</span>
              </div>
            </div>
          </div>

          <Separator />

          <!-- Dates -->
          <div class="space-y-4">
            <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
              Timeline
            </h4>
            <div class="grid grid-cols-2 gap-4">
              <div class="space-y-1">
                <p class="text-sm font-medium flex items-center gap-2">
                  <IconCalendar class="h-4 w-4 text-muted-foreground" />
                  Created
                </p>
                <p class="text-sm text-muted-foreground pl-6">
                  {{ formatDate(post.created_at) }}
                </p>
              </div>
              <div class="space-y-1">
                <p class="text-sm font-medium flex items-center gap-2">
                  <IconCalendar class="h-4 w-4 text-muted-foreground" />
                  Updated
                </p>
                <p class="text-sm text-muted-foreground pl-6">
                  {{ formatDate(post.updated_at) }}
                </p>
              </div>
            </div>
          </div>

          <!-- Flagged Info -->
          <div
            v-if="post.is_flagged && post.flagged_reason"
            class="space-y-4 rounded-lg border border-destructive/20 bg-destructive/5 p-4"
          >
            <h4 class="text-sm font-medium text-destructive flex items-center gap-2">
              <IconFlag class="h-4 w-4" />
              Flag Information
            </h4>
            <div class="space-y-2">
              <p class="text-sm font-medium">Reason:</p>
              <p class="text-sm text-muted-foreground italic">
                {{ post.flagged_reason }}
              </p>
              <p v-if="post.flagged_at" class="text-xs text-muted-foreground">
                Flagged on: {{ formatDate(post.flagged_at) }}
              </p>
            </div>
          </div>

          <!-- Deleted Info -->
          <div
            v-if="post.is_deleted"
            class="space-y-4 rounded-lg border border-destructive/20 bg-destructive/5 p-4"
          >
            <h4 class="text-sm font-medium text-destructive flex items-center gap-2">
              <IconTrash class="h-4 w-4" />
              Deletion Information
            </h4>
            <div class="space-y-2">
              <p v-if="post.deleted_at" class="text-xs text-muted-foreground">
                Deleted on: {{ formatDate(post.deleted_at) }}
              </p>
            </div>
          </div>

          <Separator />

          <!-- Content Preview -->
          <div class="space-y-4">
            <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
              Content Preview
            </h4>
            <div class="rounded-lg border p-4">
              <p class="text-sm text-muted-foreground whitespace-pre-wrap">
                {{ post.content || post.excerpt || 'No content available' }}
              </p>
            </div>
          </div>

          <!-- IDs -->
          <div class="space-y-4">
            <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
              Identifiers
            </h4>
            <div class="grid gap-2 text-xs">
              <div class="flex items-center gap-2">
                <span class="text-muted-foreground">Post ID:</span>
                <code class="bg-muted px-1.5 py-0.5 rounded font-mono">{{ post.id }}</code>
              </div>
              <div class="flex items-center gap-2">
                <span class="text-muted-foreground">Author ID:</span>
                <code class="bg-muted px-1.5 py-0.5 rounded font-mono">{{ post.user_id }}</code>
              </div>
              <div class="flex items-center gap-2">
                <span class="text-muted-foreground">Community ID:</span>
                <code class="bg-muted px-1.5 py-0.5 rounded font-mono">{{
                  post.community_id
                }}</code>
              </div>
            </div>
          </div>
        </div>
      </ScrollArea>

      <div v-else class="flex h-full items-center justify-center p-8">
        <p class="text-muted-foreground">Post not found</p>
      </div>
    </DrawerContent>
  </Drawer>
</template>
