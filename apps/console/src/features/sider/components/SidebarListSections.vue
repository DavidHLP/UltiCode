<script setup lang="ts">
import { computed } from "vue";
import { RouterLink, useRoute } from "vue-router";
import { useI18n } from "vue-i18n";
import { SidebarBranch } from "@ulticode/sidebar-menu";
import {
  SidebarGroup,
  SidebarMenuSubItem,
  SidebarMenuSubButton,
  SidebarMenuAction,
} from "@ulticode/design-system/ui/sidebar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuGroup,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@ulticode/design-system/ui/dropdown-menu";
import { Button } from "@ulticode/design-system/ui/button";
import {
  Star,
  Bookmark,
  User,
  Folder,
  MoreHorizontal,
  Trash2,
  FolderInput,
  BookmarkMinus,
  Pencil,
  AlertCircle,
  RefreshCw,
} from "@lucide/vue";
import type { Component } from "vue";
import type { ProblemList, ProblemListCategory } from "@/types/problem-list";

const props = defineProps<{
  data: {
    ownLists: ProblemList[];
    savedLists: ProblemList[];
    featuredLists: ProblemList[];
    categories: (ProblemListCategory & { lists: ProblemList[] })[];
  };
  allCategories: ProblemListCategory[];
  hasError?: boolean;
}>();
const emit = defineEmits<{
  deleteList: [list: ProblemList];
  unsaveList: [list: ProblemList];
  moveListToCategory: [list: ProblemList, categoryId: string | null];
  editCategory: [category: ProblemListCategory];
  deleteCategory: [category: ProblemListCategory];
  retryLoad: [];
}>();
const { t } = useI18n();
const route = useRoute();
interface ListSection {
  id: string;
  title: string;
  icon: Component;
  lists: ProblemList[];
  kind: "own" | "saved" | "featured" | "category";
  category?: ProblemListCategory;
}
const sections = computed<ListSection[]>(
  () =>
    [
      {
        id: "featured",
        title: t("sidebar.problemLists.featured"),
        icon: Star,
        lists: props.data.featuredLists,
        kind: "featured",
      },
      {
        id: "own",
        title: t("sidebar.problemLists.myLists"),
        icon: User,
        lists: props.data.ownLists,
        kind: "own",
      },
      {
        id: "saved",
        title: t("sidebar.problemLists.savedLists"),
        icon: Bookmark,
        lists: props.data.savedLists,
        kind: "saved",
      },
      ...props.data.categories.map((category) => ({
        id: category.id,
        title: category.name,
        icon: Folder,
        lists: category.lists,
        kind: "category" as const,
        category,
      })),
    ].filter(
      (section) => section.kind === "category" || section.lists.length > 0,
    ) as ListSection[],
);
const listUrl = (id: string | number) =>
  `/problemset/list/${encodeURIComponent(String(id))}`;
const isListActive = (id: string | number) => route.path === listUrl(id);
</script>

<template>
  <div
    v-if="hasError"
    role="status"
    class="flex items-start gap-2 px-3 py-2 text-sm text-destructive"
  >
    <AlertCircle class="size-4 shrink-0" />
    <div>
      <p>{{ t("problem.banners.unableToLoad") }}</p>
      <Button variant="outline" @click="emit('retryLoad')"
        ><RefreshCw />{{ t("common.actions.retry") }}</Button
      >
    </div>
  </div>
  <SidebarGroup v-for="section in sections" :key="section.id">
    <SidebarBranch
      :title="section.title"
      :icon="section.icon"
      :contains-active="section.lists.some((item) => isListActive(item.id))"
    >
      <template v-if="section.category" #actions>
        <DropdownMenu>
          <DropdownMenuTrigger as-child
            ><SidebarMenuAction :aria-label="section.title" show-on-hover
              ><MoreHorizontal /></SidebarMenuAction
          ></DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuGroup>
              <DropdownMenuItem @click="emit('editCategory', section.category!)"
                ><Pencil />{{ t("common.actions.edit") }}</DropdownMenuItem
              >
              <DropdownMenuItem
                @click="emit('deleteCategory', section.category!)"
                ><Trash2 />{{ t("common.actions.delete") }}</DropdownMenuItem
              >
            </DropdownMenuGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      </template>
      <SidebarMenuSubItem v-for="item in section.lists" :key="item.id">
        <SidebarMenuSubButton
          :is-active="isListActive(item.id)"
          :class="section.kind !== 'featured' ? 'pe-8' : undefined"
          as-child
        >
          <RouterLink :to="listUrl(item.id)">
            <span class="flex-1 truncate">{{ item.name }}</span>
            <span class="ml-auto shrink-0 text-xs text-muted-foreground">{{
              item.problemCount
            }}</span>
          </RouterLink>
        </SidebarMenuSubButton>
        <DropdownMenu v-if="section.kind !== 'featured'">
          <DropdownMenuTrigger as-child
            ><SidebarMenuAction
              class="md:opacity-0 group-hover/menu-sub-item:opacity-100 group-focus-within/menu-sub-item:opacity-100 aria-expanded:opacity-100"
              :aria-label="item.name"
              @click.prevent.stop
              ><MoreHorizontal /></SidebarMenuAction
          ></DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuGroup>
              <DropdownMenuItem
                v-if="section.kind === 'own'"
                @click="emit('deleteList', item)"
                ><Trash2 />{{ t("common.actions.delete") }}</DropdownMenuItem
              >
              <template v-else>
                <DropdownMenuSub
                  v-if="section.kind === 'saved' && allCategories.length"
                >
                  <DropdownMenuSubTrigger
                    ><FolderInput />{{
                      t("problem.problemList.detail.moveToCategory")
                    }}</DropdownMenuSubTrigger
                  >
                  <DropdownMenuSubContent>
                    <DropdownMenuItem
                      v-for="category in allCategories"
                      :key="category.id"
                      @click="emit('moveListToCategory', item, category.id)"
                      >{{ category.name }}</DropdownMenuItem
                    >
                  </DropdownMenuSubContent>
                </DropdownMenuSub>
                <DropdownMenuItem
                  v-if="section.kind === 'category'"
                  @click="emit('moveListToCategory', item, null)"
                  ><FolderInput />{{
                    t("problem.problemList.detail.removeFromCategory")
                  }}</DropdownMenuItem
                >
                <DropdownMenuSeparator />
                <DropdownMenuItem @click="emit('unsaveList', item)"
                  ><BookmarkMinus />{{
                    t("problem.problemList.detail.unsave")
                  }}</DropdownMenuItem
                >
              </template>
            </DropdownMenuGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuSubItem>
    </SidebarBranch>
  </SidebarGroup>
</template>
