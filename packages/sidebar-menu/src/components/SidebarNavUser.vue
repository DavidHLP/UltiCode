<script setup lang="ts">
import {
  Avatar,
  AvatarImage,
  AvatarFallback,
} from "@ulticode/design-system/ui/avatar";
import { Badge } from "@ulticode/design-system/ui/badge";
import type { SidebarUser } from "../utils";
defineProps<{ user: SidebarUser; class?: string }>();
</script>
<template>
  <div class="flex items-center gap-2 px-2 py-2" :class="$props.class">
    <slot name="avatar" :user="user"
      ><Avatar
        ><AvatarImage
          v-if="user.avatar"
          :src="user.avatar"
          :alt="user.name"
        /><AvatarFallback>{{
          user.name?.charAt(0)?.toUpperCase()
        }}</AvatarFallback></Avatar
      ></slot
    >
    <div class="min-w-0 flex-1">
      <div class="truncate text-sm font-medium">{{ user.name }}</div>
      <div v-if="user.email" class="truncate text-xs text-muted-foreground">
        {{ user.email }}
      </div>
    </div>
    <Badge v-if="user.role" variant="secondary">{{ user.role }}</Badge
    ><slot name="menu" :user="user" />
  </div>
</template>
