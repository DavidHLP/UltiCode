<script setup lang="ts">
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@ulticode/design-system/ui/collapsible";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
  DropdownMenuGroup,
} from "@ulticode/design-system/ui/dropdown-menu";
import { Button } from "@ulticode/design-system/ui/button";
import { sidebarMenuButtonVariants } from "@ulticode/design-system/ui/sidebar";
import {
  ChevronRight,
  MoreHorizontal,
  Trash2,
  FolderInput,
  Star,
  Bookmark,
  User,
  BookmarkMinus,
  Pencil,
  AlertCircle,
  RefreshCw,
} from "@lucide/vue";
import type { ProblemList, ProblemListCategory } from "@/types/problem-list";
import { RouterLink, useRoute } from "vue-router";
import { useI18n } from "vue-i18n";

const { t } = useI18n();
const route = useRoute();

const emit = defineEmits<{
  deleteList: [list: ProblemList];
  unsaveList: [list: ProblemList];
  moveListToCategory: [list: ProblemList, categoryId: string | null];
  editCategory: [category: ProblemListCategory];
  deleteCategory: [category: ProblemListCategory];
  retryLoad: [];
}>();

defineProps<{
  data: {
    ownLists: ProblemList[];
    savedLists: ProblemList[];
    featuredLists: ProblemList[];
    categories: (ProblemListCategory & { lists: ProblemList[] })[];
  };
  allCategories: ProblemListCategory[];
  hasError?: boolean;
}>();

const isListActive = (id: string | number) => {
  return route.path === `/problemset/list/${id}`;
};

// All problem-list rows share the same public sidebar-menu contract as the
// primary problem navigation. The row keeps its action menu as a sibling while
// `[data-active]` owns the active bar, surface, and text treatment.
const listRowClass = `${sidebarMenuButtonVariants()} group group/item`;
const listSectionTriggerClass =
  "uc-sidebar-section-trigger group/trigger flex-1";
</script>

<template>
  <!-- Load Failure Placeholder -->
  <div
    v-if="hasError"
    class="uc-sidebar-feedback flex items-start gap-2 text-destructive"
  >
    <AlertCircle class="h-3.5 w-3.5 mt-0.5 shrink-0" />
    <div class="flex-1 min-w-0">
      <p class="text-xs font-semibold leading-tight">
        {{ t("problem.banners.unableToLoad") }}
      </p>
      <Button
        variant="outline"
        type="button"
        class="mt-1 inline-flex items-center gap-1 underline underline-offset-2"
        @click="emit('retryLoad')"
      >
        <RefreshCw class="h-3 w-3" />
        {{ t("common.actions.retry") }}
      </Button>
    </div>
  </div>

  <!-- My Lists Section -->
  <div class="uc-sidebar-list-section" v-if="data.ownLists.length > 0">
    <Collapsible :default-open="true" class="group/collapsible">
      <div class="uc-sidebar-section-header">
        <CollapsibleTrigger :class="listSectionTriggerClass">
          <ChevronRight
            class="uc-sidebar-section-chevron group-data-[state=open]/collapsible:rotate-90"
          />
          <User class="uc-sidebar-section-icon" />
          <span>{{ t("sidebar.problemLists.myLists").toUpperCase() }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="uc-sidebar-list-content">
        <ul class="uc-sidebar-list">
          <li v-for="item in data.ownLists" :key="item.id" class="group/item">
            <div
              :class="listRowClass"
              data-sidebar="menu-button"
              data-slot="sidebar-menu-button"
              data-size="default"
              :data-active="isListActive(item.id) || undefined"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-xs text-muted-foreground font-mono">{{
                  item.problemCount
                }}</span>
              </RouterLink>

              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="uc-sidebar-icon-button w-7"
                    @click.prevent.stop
                  >
                    <MoreHorizontal class="h-3 w-3" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent
                  align="end"
                  class="uc-sidebar-dropdown w-40"
                >
                  <DropdownMenuGroup
                    ><DropdownMenuItem @click="emit('deleteList', item)">
                      <Trash2 class="mr-2 h-4 w-4" />
                      {{ t("common.actions.delete") }}
                    </DropdownMenuItem></DropdownMenuGroup
                  >
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </li>
        </ul>
      </CollapsibleContent>
    </Collapsible>
  </div>

  <!-- Saved Lists Section -->
  <div class="uc-sidebar-list-section" v-if="data.savedLists.length > 0">
    <Collapsible :default-open="true" class="group/collapsible">
      <div class="uc-sidebar-section-header">
        <CollapsibleTrigger :class="listSectionTriggerClass">
          <ChevronRight
            class="uc-sidebar-section-chevron group-data-[state=open]/collapsible:rotate-90"
          />
          <Bookmark class="uc-sidebar-section-icon" />
          <span>{{ t("sidebar.problemLists.savedLists").toUpperCase() }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="uc-sidebar-list-content">
        <ul class="uc-sidebar-list">
          <li v-for="item in data.savedLists" :key="item.id" class="group/item">
            <div
              :class="listRowClass"
              data-sidebar="menu-button"
              data-slot="sidebar-menu-button"
              data-size="default"
              :data-active="isListActive(item.id) || undefined"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-xs text-muted-foreground font-mono">{{
                  item.problemCount
                }}</span>
              </RouterLink>

              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="uc-sidebar-icon-button w-7"
                    @click.prevent.stop
                  >
                    <MoreHorizontal class="h-3 w-3" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent
                  align="end"
                  class="uc-sidebar-dropdown w-48"
                >
                  <DropdownMenuGroup
                    ><DropdownMenuSub v-if="allCategories.length > 0">
                      <DropdownMenuSubTrigger>
                        <FolderInput class="mr-2 h-4 w-4" />
                        {{ t("problem.problemList.detail.moveToCategory") }}
                      </DropdownMenuSubTrigger>
                      <DropdownMenuSubContent class="w-40">
                        <DropdownMenuItem
                          v-for="cat in allCategories"
                          :key="cat.id"
                          @click="emit('moveListToCategory', item, cat.id)"
                        >
                          {{ cat.name }}
                        </DropdownMenuItem>
                      </DropdownMenuSubContent>
                    </DropdownMenuSub>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem @click="emit('unsaveList', item)">
                      <BookmarkMinus class="mr-2 h-4 w-4" />
                      {{ t("problem.problemList.detail.unsave") }}
                    </DropdownMenuItem></DropdownMenuGroup
                  >
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </li>
        </ul>
      </CollapsibleContent>
    </Collapsible>
  </div>

  <!-- Featured Section -->
  <div class="uc-sidebar-list-section" v-if="data.featuredLists.length > 0">
    <Collapsible :default-open="true" class="group/collapsible">
      <div class="uc-sidebar-section-header">
        <CollapsibleTrigger :class="listSectionTriggerClass">
          <ChevronRight
            class="uc-sidebar-section-chevron group-data-[state=open]/collapsible:rotate-90"
          />
          <Star class="uc-sidebar-section-icon" />
          <span>{{ t("sidebar.problemLists.featured").toUpperCase() }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="uc-sidebar-list-content">
        <ul class="uc-sidebar-list">
          <li
            v-for="item in data.featuredLists"
            :key="item.id"
            class="group/item"
          >
            <div
              :class="listRowClass"
              data-sidebar="menu-button"
              data-slot="sidebar-menu-button"
              data-size="default"
              :data-active="isListActive(item.id) || undefined"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-xs text-muted-foreground font-mono">{{
                  item.problemCount
                }}</span>
              </RouterLink>
            </div>
          </li>
        </ul>
      </CollapsibleContent>
    </Collapsible>
  </div>

  <!-- User Categories -->
  <div
    v-for="category in data.categories"
    :key="category.id"
    class="uc-sidebar-list-section"
  >
    <Collapsible :default-open="true" class="group/collapsible">
      <div class="uc-sidebar-section-header">
        <CollapsibleTrigger :class="listSectionTriggerClass">
          <ChevronRight
            class="uc-sidebar-section-chevron group-data-[state=open]/collapsible:rotate-90"
          />
          <span class="truncate">{{ category.name }}</span>
        </CollapsibleTrigger>

        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button
              variant="ghost"
              size="icon"
              class="uc-sidebar-icon-button w-7"
            >
              <MoreHorizontal class="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="uc-sidebar-dropdown w-40">
            <DropdownMenuGroup
              ><DropdownMenuItem @click="emit('editCategory', category)">
                <Pencil class="mr-2 h-4 w-4" />
                {{ t("common.actions.edit") }}
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem @click="emit('deleteCategory', category)">
                <Trash2 class="mr-2 h-4 w-4" />
                {{ t("common.actions.delete") }}
              </DropdownMenuItem></DropdownMenuGroup
            >
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <CollapsibleContent class="uc-sidebar-list-content">
        <ul class="uc-sidebar-list">
          <li v-for="item in category.lists" :key="item.id" class="group/item">
            <div
              :class="listRowClass"
              data-sidebar="menu-button"
              data-slot="sidebar-menu-button"
              data-size="default"
              :data-active="isListActive(item.id) || undefined"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-xs text-muted-foreground font-mono">{{
                  item.problemCount
                }}</span>
              </RouterLink>

              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="uc-sidebar-icon-button w-7"
                    @click.prevent.stop
                  >
                    <MoreHorizontal class="h-3 w-3" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent
                  align="end"
                  class="uc-sidebar-dropdown w-48"
                >
                  <DropdownMenuGroup
                    ><DropdownMenuItem
                      @click="emit('moveListToCategory', item, null)"
                    >
                      <FolderInput class="mr-2 h-4 w-4" />
                      {{ t("problem.problemList.detail.removeFromCategory") }}
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem @click="emit('unsaveList', item)">
                      <BookmarkMinus class="mr-2 h-4 w-4" />
                      {{ t("problem.problemList.detail.unsave") }}
                    </DropdownMenuItem></DropdownMenuGroup
                  >
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </li>
        </ul>
      </CollapsibleContent>
    </Collapsible>
  </div>
</template>
