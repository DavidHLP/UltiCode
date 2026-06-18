<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  Layout,
  User,
  Check,
  FileCode,
  History,
  Settings,
  LogOut,
  LogIn,
} from "lucide-vue-next";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import type { ProblemDetail } from "@/types/problem-detail";
import { RouterLink } from "vue-router";
import { ProblemEdgeOperations } from "@/components/edge-operations";
import { useI18n } from "vue-i18n";
import { useAuthStore } from "@/stores/auth";
import ContestProblemDock from "../components/ContestProblemDock.vue";

interface Props {
  currentLayout: "leet" | "classic" | "compact" | "wide";
  problem?: ProblemDetail | null;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  "layout-change": [layout: "leet" | "classic" | "compact" | "wide"];
}>();

const { t } = useI18n();
const authStore = useAuthStore();
const isAuthenticated = computed(() => authStore.isAuthenticated);

// Layout options
const layoutOptions = computed(() => [
  {
    id: "leet",
    label: t("problem.layout.leet"),
    value: "leet",
  },
  {
    id: "classic",
    label: t("problem.layout.classic"),
    value: "classic",
  },
  {
    id: "compact",
    label: t("problem.layout.compact"),
    value: "compact",
  },
  {
    id: "wide",
    label: t("problem.layout.wide"),
    value: "wide",
  },
]);

const selectedLayout = computed({
  get: () => props.currentLayout,
  set: (value: string) => {
    emit("layout-change", value as "leet" | "classic" | "compact" | "wide");
  },
});

// --- Current user avatar (for the top-right user entry) ---
// We deliberately do not fall back to a third-party avatar service: the
// signed-in user is the source of truth for their own photo, and an external
// network dependency would be brittle and visually inconsistent with the
// terminal theme. The initials fallback below is the universal "no avatar"
// state, matching the other signed-in surfaces in the app.
const currentUserAvatarUrl = computed<string>(() => {
  const url = authStore.user?.avatar;
  return url && url.trim().length > 0 ? url : "";
});
const currentUserInitial = computed(() =>
  (authStore.userName || "?").charAt(0).toUpperCase(),
);
const avatarFailed = ref(false);
const showCurrentUserAvatarImage = computed(
  () => currentUserAvatarUrl.value !== "" && !avatarFailed.value,
);
const onCurrentUserAvatarError = () => {
  avatarFailed.value = true;
};
// Reset the failure flag whenever a new user (or refreshed avatar URL) shows
// up, otherwise a previously-broken URL would stick on initials forever.
watch(currentUserAvatarUrl, () => {
  avatarFailed.value = false;
});

// Top-right user entry: a 40px circular trigger that reads as a real
// identity chip rather than a generic person icon. Idle / hover / open /
// focused states each have a distinct visual treatment so the affordance
// is always legible against the dark header background.
const userEntryTriggerClass = [
  "relative h-10 w-10 p-0 rounded-full overflow-hidden",
  "border border-border/40 bg-transparent shadow-sm",
  "ring-0 ring-[var(--accent-electric)]/0",
  "hover:border-[var(--accent-electric)]/60 hover:shadow-md hover:ring-2 hover:ring-[var(--accent-electric)]/30",
  "data-[state=open]:border-[var(--accent-electric)] data-[state=open]:ring-2 data-[state=open]:ring-[var(--accent-electric)]/40",
  "focus-visible:ring-2 focus-visible:ring-[var(--accent-electric)]/50",
  "transition-all duration-200",
  "flex items-center justify-center cursor-pointer select-none",
].join(" ");

const userEntryFallbackClass = [
  "rounded-full",
  "bg-[var(--accent-electric)] text-white",
  "text-sm font-semibold tracking-wide",
  // Subtle inner shadow gives the solid-color fallback a touch of depth
  // when the user has no avatar URL.
  "shadow-[inset_0_-1px_0_rgba(0,0,0,0.08)]",
].join(" ");
</script>

<template>
  <div class="flex flex-1 items-center justify-end overflow-hidden">
    <div class="flex items-center gap-3 focus:outline-none">
      <!-- Group 0: Contest context dock (renders only for ?contestId=...) -->
      <ContestProblemDock />

      <!-- Group 1: Problem Operations (Vote & Save) -->
      <ProblemEdgeOperations v-if="problem" :problem="problem" />

      <!-- Divider -->
      <Separator
        v-if="problem"
        orientation="vertical"
        class="h-5 w-px bg-[var(--silver-200)] dark:bg-[var(--silver-300)] flex-none"
      />

      <!-- Group 2: View Settings (Layout Switching) -->
      <div class="flex items-center">
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button
              :aria-label="t('problem.explorer.filters')"
              class="h-8 w-8 p-0 rounded-none border border-transparent bg-transparent hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-200)] text-[var(--solarized-base01)] dark:text-[var(--solarized-base0)] hover:text-[var(--solarized-base03)] dark:hover:text-foreground data-[state=open]:bg-[var(--silver-100)] dark:data-[state=open]:bg-[var(--silver-200)] data-[state=open]:text-[var(--solarized-base03)] dark:data-[state=open]:text-foreground transition-all duration-200 flex items-center justify-center cursor-pointer select-none shadow-none"
            >
              <Layout class="h-4 w-4" aria-hidden="true" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent class="w-96 p-4">
            <div class="space-y-3">
              <div class="text-sm font-semibold text-foreground">
                {{ t("problem.explorer.filters") }}
              </div>
              <DropdownMenuRadioGroup
                :model-value="selectedLayout"
                @update:model-value="
                  (value) =>
                    value &&
                    emit(
                      'layout-change',
                      value as 'leet' | 'classic' | 'compact' | 'wide',
                    )
                "
              >
                <div class="grid grid-cols-2 gap-4">
                  <DropdownMenuRadioItem
                    v-for="option in layoutOptions"
                    :key="option.id"
                    :value="option.value"
                    class="relative flex flex-col items-center gap-2 p-2 rounded-none border-2 transition-all duration-200 cursor-pointer data-[state=checked]:border-[var(--accent-electric)] data-[state=unchecked]:border-border hover:border-muted-foreground hover:bg-muted"
                  >
                    <!-- Layout preview container -->
                    <div
                      class="w-full aspect-[4/3] bg-muted rounded-none flex items-center justify-center relative overflow-hidden border border-border"
                    >
                      <!-- Selected indicator -->
                      <div
                        v-if="option.value === selectedLayout"
                        class="absolute inset-0 border-2 border-[var(--accent-electric)] rounded-none"
                      />

                      <!-- Layout preview visualization -->
                      <div
                        v-if="option.value === 'leet'"
                        class="w-full h-full p-2 flex flex-row gap-1.5"
                      >
                        <div
                          class="flex-1 bg-[var(--background)] rounded-none border border-border"
                        ></div>
                        <div class="flex flex-col gap-1.5 flex-1">
                          <div
                            class="flex-1 bg-[var(--background)] rounded-none border border-border"
                          ></div>
                          <div
                            class="flex-1 bg-[var(--background)] rounded-none border border-border"
                          ></div>
                        </div>
                      </div>
                      <div
                        v-else-if="option.value === 'classic'"
                        class="w-full h-full p-2 flex flex-col gap-1.5"
                      >
                        <div
                          class="flex-1 bg-[var(--background)] rounded-none border border-border"
                        ></div>
                        <div class="flex gap-1.5 flex-1">
                          <div
                            class="flex-1 bg-[var(--background)] rounded-none border border-border"
                          ></div>
                          <div
                            class="flex-1 bg-[var(--background)] rounded-none border border-border"
                          ></div>
                        </div>
                      </div>
                      <div
                        v-else-if="option.value === 'compact'"
                        class="w-full h-full p-2 flex flex-row gap-1.5"
                      >
                        <div class="flex flex-col gap-1.5 w-1/3">
                          <div
                            class="flex-1 bg-[var(--background)] rounded-none border border-border"
                          ></div>
                          <div
                            class="flex-1 bg-[var(--background)] rounded-none border border-border"
                          ></div>
                        </div>
                        <div
                          class="flex-1 bg-[var(--background)] rounded-none border border-border"
                        ></div>
                      </div>
                      <div
                        v-else
                        class="w-full h-full p-2 flex flex-row gap-1.5"
                      >
                        <div
                          class="w-1/4 bg-[var(--background)] rounded-none border border-border"
                        ></div>
                        <div
                          class="flex-1 bg-[var(--background)] rounded-none border border-border"
                        ></div>
                        <div
                          class="w-1/4 bg-[var(--background)] rounded-none border border-border"
                        ></div>
                      </div>

                      <!-- Check mark indicator -->
                      <div
                        v-if="option.value === selectedLayout"
                        class="absolute top-1.5 right-1.5 w-5 h-5 bg-[var(--accent-electric)] rounded-none flex items-center justify-center shadow-[var(--shadow-float)]"
                      >
                        <Check class="w-3 h-3 text-white" />
                      </div>
                    </div>

                    <!-- Label -->
                    <span class="text-xs font-medium text-foreground mt-1">{{
                      option.label
                    }}</span>
                  </DropdownMenuRadioItem>
                </div>
              </DropdownMenuRadioGroup>
            </div>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <!-- Divider -->
      <Separator
        orientation="vertical"
        class="h-5 w-px bg-[var(--silver-200)] dark:bg-[var(--silver-300)] flex-none"
      />

      <!-- Group 3: User Entry -->
      <div class="flex items-center">
        <!-- Guest: show login button -->
        <RouterLink v-if="!isAuthenticated" to="/login" class="flex-none">
          <Button
            :aria-label="t('auth.login.submit')"
            class="h-8 w-8 p-0 rounded-none border border-transparent bg-transparent hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-200)] text-[var(--solarized-base01)] dark:text-[var(--solarized-base0)] hover:text-[var(--solarized-base03)] dark:hover:text-foreground transition-all duration-200 flex items-center justify-center cursor-pointer select-none shadow-none"
          >
            <LogIn class="h-4 w-4" aria-hidden="true" />
          </Button>
        </RouterLink>

        <!-- Authenticated: user dropdown -->
        <DropdownMenu v-else>
          <DropdownMenuTrigger as-child>
            <Button
              :aria-label="t('personal.profile.title')"
              :class="userEntryTriggerClass"
            >
              <Avatar class="h-10 w-10 rounded-full">
                <img
                  v-if="showCurrentUserAvatarImage"
                  :src="currentUserAvatarUrl"
                  :alt="authStore.userName"
                  class="aspect-square size-full rounded-full object-cover"
                  referrerpolicy="no-referrer"
                  @error="onCurrentUserAvatarError"
                />
                <AvatarFallback :class="userEntryFallbackClass">
                  {{ currentUserInitial }}
                </AvatarFallback>
              </Avatar>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            class="w-60 p-1.5 rounded-none border border-border/60 bg-popover text-popover-foreground shadow-md"
            align="end"
            :side-offset="8"
          >
            <DropdownMenuLabel
              class="px-2 py-1.5 text-xs font-semibold tracking-wide text-muted-foreground"
            >
              {{ t("problem.layout.userAccount") }}
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuGroup>
              <RouterLink to="/personal">
                <DropdownMenuItem
                  class="cursor-pointer rounded-none focus:bg-[var(--accent-electric)]/10 focus:text-foreground"
                >
                  <User class="mr-2 h-4 w-4" aria-hidden="true" />
                  <span>{{ t("personal.profile.title") }}</span>
                </DropdownMenuItem>
              </RouterLink>
              <RouterLink to="/personal/solutions">
                <DropdownMenuItem
                  class="cursor-pointer rounded-none focus:bg-[var(--accent-electric)]/10 focus:text-foreground"
                >
                  <FileCode class="mr-2 h-4 w-4" aria-hidden="true" />
                  <span>{{ t("personal.solutions.title") }}</span>
                </DropdownMenuItem>
              </RouterLink>
              <RouterLink to="/personal/submissions">
                <DropdownMenuItem
                  class="cursor-pointer rounded-none focus:bg-[var(--accent-electric)]/10 focus:text-foreground"
                >
                  <History class="mr-2 h-4 w-4" aria-hidden="true" />
                  <span>{{ t("personal.submissions.title") }}</span>
                </DropdownMenuItem>
              </RouterLink>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <RouterLink to="/personal/account">
              <DropdownMenuItem
                class="cursor-pointer rounded-none focus:bg-[var(--accent-electric)]/10 focus:text-foreground"
              >
                <Settings class="mr-2 h-4 w-4" aria-hidden="true" />
                <span>{{ t("personal.account.title") }}</span>
              </DropdownMenuItem>
            </RouterLink>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              class="text-[var(--terminal-red)] cursor-pointer rounded-none focus:bg-[var(--terminal-red)]/10 focus:text-[var(--terminal-red)]"
            >
              <LogOut class="mr-2 h-4 w-4" aria-hidden="true" />
              <span>{{ t("problem.layout.logout") }}</span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  </div>
</template>
