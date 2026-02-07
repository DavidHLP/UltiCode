<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Badge } from '@/components/ui/badge'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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
import type { ForumPostDetail } from '@/api/admin/forum'

const props = defineProps<{
  post: ForumPostDetail
}>()

const { t } = useI18n()

const authorInitials = computed(() => {
  if (!props.post.author?.username) return '?'
  return props.post.author.username.slice(0, 2).toUpperCase()
})

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleString()
}
</script>

<template>
  <div class="space-y-6">
    <!-- Post Header Card -->
    <Card>
      <CardHeader>
        <div class="flex items-start gap-4">
          <div
            class="h-14 w-14 rounded-xl bg-primary/10 flex items-center justify-center text-primary flex-shrink-0"
          >
            <IconMessageCircle class="h-7 w-7" />
          </div>
          <div class="flex-1 min-w-0">
            <CardTitle class="text-xl mb-3">{{ post.title }}</CardTitle>
            <div class="flex flex-wrap gap-2">
              <Badge v-if="post.is_pinned" variant="default">
                <IconPin class="h-3 w-3 mr-1" />
                {{ t('forum.status.pinned') }}
              </Badge>
              <Badge v-if="post.is_locked" variant="secondary">
                <IconLock class="h-3 w-3 mr-1" />
                {{ t('forum.status.locked') }}
              </Badge>
              <Badge v-if="post.is_flagged" variant="destructive">
                <IconFlag class="h-3 w-3 mr-1" />
                {{ t('forum.status.flagged') }}
              </Badge>
              <Badge v-if="post.is_deleted" variant="destructive">
                <IconTrash class="h-3 w-3 mr-1" />
                {{ t('forum.status.deleted') }}
              </Badge>
              <Badge
                v-if="!post.is_pinned && !post.is_locked && !post.is_flagged && !post.is_deleted"
                variant="outline"
              >
                {{ t('forum.status.active') }}
              </Badge>
            </div>
          </div>
        </div>
      </CardHeader>
      <CardContent class="space-y-4">
        <!-- Author & Community -->
        <div class="flex items-center gap-3">
          <Avatar class="h-10 w-10">
            <AvatarImage :src="post.author?.avatar || ''" :alt="post.author?.username" />
            <AvatarFallback>{{ authorInitials }}</AvatarFallback>
          </Avatar>
          <div class="flex flex-col">
            <span class="font-medium text-sm">{{
              post.author?.username || t('forum.overview.unknown')
            }}</span>
            <span class="text-xs text-muted-foreground">
              {{
                t('forum.detail.inCommunity', {
                  community: post.community?.name || t('forum.drawer.unknownCommunity'),
                })
              }}
            </span>
          </div>
        </div>

        <Separator />

        <!-- Content -->
        <div class="space-y-2">
          <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
            {{ t('forum.detail.content') }}
          </h4>
          <div class="rounded-lg border p-4">
            <p class="text-sm whitespace-pre-wrap">
              {{
                post.full_content ||
                post.content ||
                post.excerpt ||
                t('forum.detail.noContentAvailable')
              }}
            </p>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Statistics Cards -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <Card>
        <CardContent class="p-4 flex flex-col items-center justify-center text-center">
          <IconEye class="h-6 w-6 text-blue-500 mb-2" />
          <span class="text-2xl font-bold tabular-nums">{{ post.view_count || 0 }}</span>
          <span class="text-xs text-muted-foreground uppercase">{{ t('forum.detail.views') }}</span>
        </CardContent>
      </Card>
      <Card>
        <CardContent class="p-4 flex flex-col items-center justify-center text-center">
          <IconMessage class="h-6 w-6 text-purple-500 mb-2" />
          <span class="text-2xl font-bold tabular-nums">{{ post.comment_count || 0 }}</span>
          <span class="text-xs text-muted-foreground uppercase">{{
            t('forum.detail.comments')
          }}</span>
        </CardContent>
      </Card>
      <Card>
        <CardContent class="p-4 flex flex-col items-center justify-center text-center">
          <IconThumbUp class="h-6 w-6 text-emerald-500 mb-2" />
          <span class="text-2xl font-bold tabular-nums">{{ post.upvotes || 0 }}</span>
          <span class="text-xs text-muted-foreground uppercase">{{
            t('forum.detail.upvotes')
          }}</span>
        </CardContent>
      </Card>
      <Card>
        <CardContent class="p-4 flex flex-col items-center justify-center text-center">
          <IconThumbDown class="h-6 w-6 text-rose-500 mb-2" />
          <span class="text-2xl font-bold tabular-nums">{{ post.downvotes || 0 }}</span>
          <span class="text-xs text-muted-foreground uppercase">{{
            t('forum.detail.downvotes')
          }}</span>
        </CardContent>
      </Card>
    </div>

    <!-- Timeline -->
    <Card>
      <CardHeader>
        <CardTitle class="text-base">{{ t('forum.detail.timeline') }}</CardTitle>
      </CardHeader>
      <CardContent>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="space-y-1">
            <p class="text-sm font-medium flex items-center gap-2">
              <IconCalendar class="h-4 w-4 text-muted-foreground" />
              {{ t('forum.detail.created') }}
            </p>
            <p class="text-sm text-muted-foreground pl-6">{{ formatDate(post.created_at) }}</p>
          </div>
          <div class="space-y-1">
            <p class="text-sm font-medium flex items-center gap-2">
              <IconCalendar class="h-4 w-4 text-muted-foreground" />
              {{ t('forum.detail.updated') }}
            </p>
            <p class="text-sm text-muted-foreground pl-6">{{ formatDate(post.updated_at) }}</p>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Flagged Info -->
    <Card
      v-if="post.is_flagged && post.flagged_reason"
      class="border-destructive/20 bg-destructive/5"
    >
      <CardHeader>
        <CardTitle class="text-base text-destructive flex items-center gap-2">
          <IconFlag class="h-4 w-4" />
          {{ t('forum.detail.flagInformation') }}
        </CardTitle>
      </CardHeader>
      <CardContent class="space-y-3">
        <div>
          <p class="text-sm font-medium mb-1">{{ t('forum.detail.reason') }}</p>
          <p class="text-sm italic text-muted-foreground">{{ post.flagged_reason }}</p>
        </div>
        <p v-if="post.flagged_at" class="text-xs text-muted-foreground">
          {{ t('forum.detail.flaggedOn') }} {{ formatDate(post.flagged_at) }}
        </p>
      </CardContent>
    </Card>

    <!-- Deleted Info -->
    <Card v-if="post.is_deleted" class="border-destructive/20 bg-destructive/5">
      <CardHeader>
        <CardTitle class="text-base text-destructive flex items-center gap-2">
          <IconTrash class="h-4 w-4" />
          {{ t('forum.detail.deletionInformation') }}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <p v-if="post.deleted_at" class="text-xs text-muted-foreground">
          {{ t('forum.detail.deletedOn') }} {{ formatDate(post.deleted_at) }}
        </p>
      </CardContent>
    </Card>

    <!-- Identifiers -->
    <Card>
      <CardHeader>
        <CardTitle class="text-base">{{ t('forum.detail.identifiers') }}</CardTitle>
      </CardHeader>
      <CardContent>
        <div class="grid gap-2 text-xs">
          <div class="flex items-center gap-2">
            <span class="text-muted-foreground">{{ t('forum.detail.postId') }}</span>
            <code class="bg-muted px-1.5 py-0.5 rounded font-mono">{{ post.id }}</code>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-muted-foreground">{{ t('forum.detail.authorId') }}</span>
            <code class="bg-muted px-1.5 py-0.5 rounded font-mono">{{ post.user_id }}</code>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-muted-foreground">{{ t('forum.detail.communityId') }}</span>
            <code class="bg-muted px-1.5 py-0.5 rounded font-mono">{{ post.community_id }}</code>
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
