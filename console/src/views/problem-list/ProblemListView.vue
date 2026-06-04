<script setup lang="ts">
import { computed, ref, nextTick } from "vue";
import { useRoute } from "vue-router";
import ProblemExplorer from "@/components/problem/ProblemExplorer.vue";
import { Button } from "@/components/ui/button";
import {
  GitFork,
  MoreHorizontal,
  Share2,
  CalendarDays,
  Clock,
  Copy,
  Pencil,
  Trash2,
  Plus,
  Bookmark,
  BookmarkCheck,
  FolderInput,
  BookmarkMinus,
  ListX,
} from "lucide-vue-next";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
} from "@/components/ui/empty";
import { Badge } from "@/components/ui/badge";
import { SemanticBadge } from "@/components/ui/terminal";
import { Separator } from "@/components/ui/separator";
import { useI18n } from "vue-i18n";
import ProblemListAnalytics from "./ProblemListAnalytics.vue";
import EditListDialog from "./components/EditListDialog.vue";
import DeleteListDialog from "./components/DeleteListDialog.vue";
import AddProblemsDialog from "./components/AddProblemsDialog.vue";
import { useProblemListOperations } from "./composables/useProblemListOperations";

const route = useRoute();
const { t } = useI18n();
const listId = computed(() => route.params.id as string);

const {
  currentList,
  problems,
  isSaved,
  isSaving,
  isForking,
  isDeleting,
  editForm,
  userCategories,
  currentCategoryId,
  isOwner,
  canSave,
  problemIdsInList,
  formatDate,
  handleShare,
  handleFork,
  handleDelete,
  handleSaveEdit,
  handleToggleSave,
  handleMoveToCategory,
  handleAddProblem,
  handleRemoveProblem,
} = useProblemListOperations(listId);

const problemsWithStatus = computed(() => problems.value);

// Dialog state (D-04: stays in parent)
const isEditOpen = ref(false);
const isDeleteOpen = ref(false);
const isAddProblemsOpen = ref(false);
const dropdownOpen = ref(false);

async function onSaveEdit() {
  const success = await handleSaveEdit();
  if (success) isEditOpen.value = false;
}

async function onDeleteConfirm() {
  await handleDelete();
  isDeleteOpen.value = false;
}

function openAddProblemsDialog() {
  isAddProblemsOpen.value = true;
}
</script>

<template>
  <div
    class="container mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8 max-w-[1400px]"
  >
    <!-- Header Section -->
    <div
      class="flex flex-col gap-6 md:flex-row md:items-start md:justify-between border-b pb-8"
    >
      <div class="space-y-4 max-w-3xl">
        <div class="space-y-2">
          <div class="flex items-center gap-3">
            <Badge variant="outline" class="rounded-none px-2.5 py-0.5">{{
              t("problem.problemList.detail.listBadge")
            }}</Badge>
            <SemanticBadge
              v-if="currentList?.isPublic"
              color="success"
              :label="t('problem.problemList.listCard.public', 'Public')"
              size="sm"
            />
            <SemanticBadge
              v-else
              color="warning"
              :label="t('problem.problemList.listCard.private', 'Private')"
              size="sm"
            />
          </div>
          <h1
            class="text-3xl font-bold tracking-tight sm:text-4xl text-foreground/90"
          >
            {{ currentList?.name || t("problem.problemList.detail.listBadge") }}
          </h1>
        </div>

        <p
          v-if="currentList?.description"
          class="text-lg text-muted-foreground/80 leading-relaxed"
        >
          {{ currentList.description }}
        </p>

        <div
          class="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-muted-foreground/70"
        >
          <div class="flex items-center gap-2">
            <div
              class="w-8 h-8 rounded-full bg-muted flex items-center justify-center text-xs font-medium"
            >
              {{ currentList?.authorId?.slice(0, 2).toUpperCase() || "U" }}
            </div>
            <span>{{ t("problem.problemList.detail.author") }}</span>
          </div>

          <Separator orientation="vertical" class="h-4" />

          <span v-if="currentList?.createdAt" class="flex items-center gap-1.5">
            <CalendarDays class="w-4 h-4" />
            {{ t("problem.problemList.detail.created") }}
            {{ formatDate(currentList.createdAt) }}
          </span>

          <span v-if="currentList?.updatedAt" class="flex items-center gap-1.5">
            <Clock class="w-4 h-4" />
            {{ t("problem.problemList.detail.updated") }}
            {{ formatDate(currentList.updatedAt) }}
          </span>
        </div>
      </div>

      <div class="flex items-center gap-2 shrink-0">
        <Button
          v-if="canSave"
          :variant="isSaved ? 'default' : 'outline'"
          size="sm"
          class="h-9 gap-2"
          @click="handleToggleSave"
          :disabled="isSaving"
        >
          <BookmarkCheck v-if="isSaved" class="h-4 w-4" />
          <Bookmark v-else class="h-4 w-4" />
          {{
            isSaving
              ? t("problem.problemList.detail.saving")
              : isSaved
                ? t("problem.problemList.detail.saved")
                : t("problem.problemList.detail.save")
          }}
        </Button>
        <Button variant="secondary" size="sm" class="h-9" @click="handleShare">
          <Share2 class="mr-2 h-4 w-4" />
          {{ t("problem.problemList.detail.share") }}
        </Button>
        <Button
          variant="outline"
          size="sm"
          class="h-9"
          @click="handleFork"
          :disabled="isForking"
        >
          <GitFork class="mr-2 h-4 w-4" />
          {{
            isForking
              ? t("problem.problemList.detail.forking")
              : t("problem.problemList.detail.fork")
          }}
        </Button>
        <DropdownMenu v-model:open="dropdownOpen">
          <DropdownMenuTrigger as-child>
            <Button variant="ghost" size="icon" class="h-9 w-9">
              <MoreHorizontal class="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="w-56">
            <template v-if="isOwner">
              <DropdownMenuItem
                @click="
                  dropdownOpen = false;
                  nextTick(() => (isEditOpen = true));
                "
              >
                <Pencil class="mr-2 h-4 w-4" />
                {{ t("problem.problemList.detail.editDetails") }}
              </DropdownMenuItem>
              <DropdownMenuItem
                @click="
                  dropdownOpen = false;
                  nextTick(() => openAddProblemsDialog());
                "
              >
                <Plus class="mr-2 h-4 w-4" />
                {{ t("problem.problemList.detail.addProblems") }}
              </DropdownMenuItem>
            </template>
            <template v-if="canSave && isSaved">
              <DropdownMenuSub v-if="userCategories.length > 0">
                <DropdownMenuSubTrigger>
                  <FolderInput class="mr-2 h-4 w-4" />
                  {{ t("problem.problemList.detail.moveToCategory") }}
                </DropdownMenuSubTrigger>
                <DropdownMenuSubContent>
                  <DropdownMenuItem
                    v-if="currentCategoryId"
                    @click="handleMoveToCategory(null)"
                  >
                    {{ t("problem.problemList.detail.removeFromCategory") }}
                  </DropdownMenuItem>
                  <DropdownMenuSeparator v-if="currentCategoryId" />
                  <DropdownMenuItem
                    v-for="cat in userCategories"
                    :key="cat.id"
                    @click="handleMoveToCategory(cat.id)"
                  >
                    {{ cat.name }}
                  </DropdownMenuItem>
                </DropdownMenuSubContent>
              </DropdownMenuSub>
              <DropdownMenuItem @click="handleToggleSave">
                <BookmarkMinus class="mr-2 h-4 w-4" />
                {{ t("problem.problemList.detail.unsave") }}
              </DropdownMenuItem>
              <DropdownMenuSeparator />
            </template>
            <DropdownMenuItem @click="handleFork" :disabled="isForking">
              <Copy class="mr-2 h-4 w-4" />
              {{ t("problem.problemList.detail.duplicate") }}
            </DropdownMenuItem>
            <template v-if="isOwner">
              <DropdownMenuSeparator />
              <DropdownMenuItem
                class="text-destructive focus:text-destructive"
                @click="
                  dropdownOpen = false;
                  nextTick(() => (isDeleteOpen = true));
                "
              >
                <Trash2 class="mr-2 h-4 w-4" />
                {{ t("problem.problemList.actions.deleteList") }}
              </DropdownMenuItem>
            </template>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>

    <!-- Dialogs -->
    <EditListDialog
      :open="isEditOpen"
      :form="editForm"
      @update:open="isEditOpen = $event"
      @submit="onSaveEdit"
    />

    <DeleteListDialog
      :open="isDeleteOpen"
      :list-name="currentList?.name"
      :loading="isDeleting"
      @update:open="isDeleteOpen = $event"
      @confirm="onDeleteConfirm"
    />

    <AddProblemsDialog
      :open="isAddProblemsOpen"
      :list-id="listId"
      :problem-ids-in-list="problemIdsInList"
      @update:open="isAddProblemsOpen = $event"
      @add="handleAddProblem"
    />

    <div v-if="problems.length === 0" class="py-12">
      <Empty
        class="h-80 border border-dashed border-border/60 bg-muted/20 rounded-none"
      >
        <EmptyContent>
          <EmptyMedia
            variant="icon"
            class="bg-background p-4 rounded-full shadow-sm mb-4"
          >
            <ListX class="h-8 w-8 text-muted-foreground" />
          </EmptyMedia>
          <EmptyHeader>
            <h3 class="text-xl font-semibold text-foreground mb-1">
              {{ t("problem.problemList.detail.emptyTitle") }}
            </h3>
            <EmptyDescription class="text-base">
              {{ t("problem.problemList.detail.emptyDesc") }}
            </EmptyDescription>
          </EmptyHeader>
          <Button
            class="mt-6"
            size="lg"
            @click="openAddProblemsDialog"
            v-if="isOwner"
            >{{ t("problem.problemList.detail.addProblems") }}</Button
          >
          <Button class="mt-6" size="lg" @click="handleFork" v-else>{{
            t("problem.problemList.detail.forkAndAdd")
          }}</Button>
        </EmptyContent>
      </Empty>
    </div>

    <!-- Main Content Grid -->
    <div v-else class="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
      <!-- Left Column: Problem List (8 cols) -->
      <div class="lg:col-span-8 space-y-6">
        <ProblemExplorer
          :problems="problemsWithStatus"
          :editable="isOwner"
          @remove="handleRemoveProblem"
        />
      </div>

      <!-- Right Column: Analytics Sidebar (4 cols) -->
      <div class="lg:col-span-4 space-y-6 sticky top-6">
        <ProblemListAnalytics :problems="problemsWithStatus" />
      </div>
    </div>
  </div>
</template>
