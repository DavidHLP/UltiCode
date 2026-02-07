<script setup lang="ts">
import { ref, computed, watch } from "vue";
import {
  ListPlus,
  ListCheck,
  FolderPlus,
  Loader2,
  Check,
} from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuCheckboxItem,
  DropdownMenuSeparator,
  DropdownMenuItem,
  DropdownMenuLabel,
} from "@/components/ui/dropdown-menu";
import {
  getUserListsForProblem,
  batchAddProblemToLists,
  batchRemoveProblemFromLists,
  createProblemList,
  type ProblemListWithStatus,
} from "@/api/problem-list";
import { toast } from "vue-sonner";
import { isAuthenticated, fetchCurrentUserId } from "@/utils/auth";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  problemId: number;
  variant?: "default" | "ghost" | "outline";
  size?: "default" | "sm" | "icon";
}>();

const emit = defineEmits<{
  change: [listIds: string[]];
}>();

const { t } = useI18n();

const userLists = ref<ProblemListWithStatus[]>([]);
const isLoading = ref(false);
const isOpen = ref(false);
const isAuthed = ref(false);
const showCreateDialog = ref(false);
const newListName = ref("");
const isCreating = ref(false);

// Track which lists contain this problem
const selectedListIds = computed(() =>
  userLists.value.filter((list) => list.containsProblem).map((list) => list.id),
);

const hasAnyList = computed(() => selectedListIds.value.length > 0);

async function loadData() {
  if (!isOpen.value) return;

  isAuthed.value = isAuthenticated();
  if (!isAuthed.value) {
    userLists.value = [];
    isLoading.value = false;
    return;
  }

  const userId = fetchCurrentUserId();
  if (!userId) {
    userLists.value = [];
    isLoading.value = false;
    return;
  }

  isLoading.value = true;
  try {
    userLists.value = await getUserListsForProblem(userId, props.problemId);
  } catch (error) {
    console.error("Failed to load user lists:", error);
    toast.error(t("problem.save.toast.loadListsFailed"));
  } finally {
    isLoading.value = false;
  }
}

async function toggleList(listId: string) {
  if (!isAuthenticated()) {
    toast.error(t("problem.save.toast.loginRequired"));
    return;
  }

  const userId = fetchCurrentUserId();
  if (!userId) return;

  const list = userLists.value.find((l) => l.id === listId);
  if (!list) return;

  const isInList = list.containsProblem;

  try {
    if (isInList) {
      // Remove from this list
      await batchRemoveProblemFromLists(userId, props.problemId, [listId]);
      list.containsProblem = false;
      toast.success(
        t("problem.save.toast.removedFromList", { name: list.name }),
      );
    } else {
      // Add to this list
      await batchAddProblemToLists(userId, props.problemId, [listId]);
      list.containsProblem = true;
      toast.success(t("problem.save.toast.addedToList", { name: list.name }));
    }

    emit("change", selectedListIds.value);
  } catch (error) {
    console.error("Failed to toggle list:", error);
    toast.error(t("problem.save.toast.updateListFailed"));
  }
}

function handleOpenChange(open: boolean) {
  isOpen.value = open;
  if (open) {
    loadData();
  }
}

function openCreateDialog() {
  showCreateDialog.value = true;
  newListName.value = "";
}

async function handleCreateList() {
  if (!newListName.value.trim()) {
    toast.error(t("problem.save.toast.enterName"));
    return;
  }

  const userId = fetchCurrentUserId();
  if (!userId) return;

  isCreating.value = true;
  try {
    const newList = await createProblemList(userId, {
      name: newListName.value.trim(),
      isPublic: false,
    });

    // Add the problem to the newly created list
    await batchAddProblemToLists(userId, props.problemId, [newList.id]);

    // Reload the lists
    await loadData();

    toast.success(
      t("problem.save.toast.createdAndAdded", { name: newList.name }),
    );
    showCreateDialog.value = false;
  } catch (error) {
    console.error("Failed to create list:", error);
    toast.error(t("problem.save.toast.createFailed"));
  } finally {
    isCreating.value = false;
  }
}

watch(
  () => props.problemId,
  () => {
    userLists.value = [];
  },
);
</script>

<template>
  <div>
    <DropdownMenu @update:open="handleOpenChange">
      <DropdownMenuTrigger as-child>
        <Button
          :variant="variant ?? 'ghost'"
          :size="size ?? 'icon'"
          class="relative rounded-full"
          :aria-label="t('problem.save.addToProblemList')"
        >
          <component
            :is="hasAnyList ? ListCheck : ListPlus"
            class="h-4 w-4"
            :class="{ 'text-primary': hasAnyList }"
          />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent class="w-72" align="end">
        <DropdownMenuLabel>{{
          t("problem.save.addToProblemList")
        }}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <template v-if="isLoading">
          <div class="flex items-center justify-center py-4">
            <Loader2 class="h-4 w-4 animate-spin" />
          </div>
        </template>
        <template v-else-if="!isAuthed">
          <div class="px-3 py-2 text-sm text-muted-foreground">
            {{ t("problem.save.loginRequired") }}
          </div>
        </template>
        <template v-else>
          <div
            v-if="userLists.length === 0"
            class="px-3 py-4 text-sm text-muted-foreground text-center"
          >
            <p class="mb-2">{{ t("problem.save.emptyLists") }}</p>
            <p class="text-xs">{{ t("problem.save.createOneHint") }}</p>
          </div>
          <div v-else class="max-h-[300px] overflow-y-auto">
            <DropdownMenuCheckboxItem
              v-for="list in userLists"
              :key="list.id"
              :checked="list.containsProblem"
              @select.prevent="toggleList(list.id)"
              class="flex items-start gap-2 py-2"
            >
              <div class="flex flex-col flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <span class="font-medium truncate">{{ list.name }}</span>
                  <Check
                    v-if="list.containsProblem"
                    class="h-3 w-3 text-primary flex-shrink-0"
                  />
                </div>
                <div
                  class="flex items-center gap-2 text-xs text-muted-foreground"
                >
                  <span>{{ list.problemCount }} {{ t("bookmark.items") }}</span>
                  <span>•</span>
                  <span
                    >{{ list.favoritesCount }}
                    {{ t("problem.save.saves") }}</span
                  >
                </div>
              </div>
            </DropdownMenuCheckboxItem>
          </div>

          <DropdownMenuSeparator />

          <DropdownMenuItem class="cursor-pointer" @select="openCreateDialog">
            <FolderPlus class="mr-2 h-4 w-4" />
            {{ t("problem.save.createNewList") }}
          </DropdownMenuItem>
        </template>
      </DropdownMenuContent>
    </DropdownMenu>

    <Dialog v-model:open="showCreateDialog">
      <DialogContent class="sm:max-w-[425px] rounded-2xl">
        <DialogHeader>
          <DialogTitle>{{ t("problem.save.createListTitle") }}</DialogTitle>
          <DialogDescription>
            {{ t("problem.save.createListDesc") }}
          </DialogDescription>
        </DialogHeader>
        <div class="grid gap-4 py-4">
          <div class="grid gap-2">
            <Label for="list-name">{{ t("problem.save.listName") }}</Label>
            <Input
              id="list-name"
              v-model="newListName"
              :placeholder="t('problem.save.listNamePlaceholder')"
              class="rounded-lg"
              @keyup.enter="handleCreateList"
            />
          </div>
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            @click="showCreateDialog = false"
            :disabled="isCreating"
            class="rounded-full"
          >
            {{ t("common.actions.cancel") }}
          </Button>
          <Button
            @click="handleCreateList"
            :disabled="isCreating"
            class="rounded-full"
          >
            <Loader2 v-if="isCreating" class="mr-2 h-4 w-4 animate-spin" />
            {{ t("problem.save.createAndAdd") }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
