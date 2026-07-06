<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
} from "@/components/ui/dropdown-menu";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Slider } from "@/components/ui/slider";
import { useEditorSettingsStore } from "@/stores/editorSettings";
import { useEditorThemes } from "@/composables/useEditorThemes";
import {
  Sun,
  Moon,
  Contrast,
  Type,
  Keyboard,
  Code2,
  RotateCcw,
} from "lucide-vue-next";
import KeyboardShortcutsModal from "./KeyboardShortcutsModal.vue";
import CodeTemplatesModal from "./CodeTemplatesModal.vue";
import AccessibilitySettings from "./AccessibilitySettings.vue";
import type { CodeTemplate } from "@/constants/codeTemplates";

const { t } = useI18n();

defineProps<{
  language: string;
}>();

const emit = defineEmits<{
  (e: "insertTemplate", code: string): void;
}>();

const settingsStore = useEditorSettingsStore();
const { themeOptions, currentTheme } = useEditorThemes();

const showShortcuts = ref(false);
const showTemplates = ref(false);

const themeIcon = computed(() => {
  switch (currentTheme.value) {
    case "vs-dark":
      return Moon;
    case "vs-light":
      return Sun;
    case "hc-black":
      return Contrast;
    default:
      return Moon;
  }
});

const fontSize = computed({
  get: () => [settingsStore.settings.fontSize],
  set: (value) => {
    if (value[0] !== undefined) {
      settingsStore.setFontSize(value[0]);
    }
  },
});

const tabSize = computed({
  get: () => settingsStore.settings.tabSize,
  set: (value) => settingsStore.setTabSize(value),
});

const handleThemeChange = (theme: unknown) => {
  if (
    typeof theme === "string" &&
    (theme === "vs-dark" || theme === "vs-light" || theme === "hc-black")
  ) {
    settingsStore.setTheme(theme);
  }
};

const handleInsertTemplate = (template: CodeTemplate) => {
  emit("insertTemplate", template.code);
  showTemplates.value = false;
};

const handleResetSettings = () => {
  settingsStore.resetToDefaults();
};
</script>

<template>
  <div class="flex items-center gap-0.5">
    <!-- Theme Selector -->
    <DropdownMenu>
      <DropdownMenuTrigger as-child>
        <Button
          variant="ghost"
          size="icon"
          class="h-6 w-6 rounded-none text-foreground hover:bg-accent hover:text-accent-foreground data-[state=open]:bg-accent data-[state=open]:text-accent-foreground"
          :aria-label="$t('problem.editor.theme')"
          :title="t('problem.editor.changeTheme')"
        >
          <component :is="themeIcon" class="h-3.5 w-3.5" aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" class="w-48">
        <DropdownMenuLabel>{{ t("problem.editor.theme") }}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuRadioGroup
          :model-value="currentTheme"
          @update:model-value="handleThemeChange"
        >
          <DropdownMenuRadioItem
            v-for="theme in themeOptions"
            :key="theme.value"
            :value="theme.value"
            class="cursor-pointer"
          >
            <div class="flex items-center gap-2">
              <component
                :is="
                  theme.value === 'vs-dark'
                    ? Moon
                    : theme.value === 'vs-light'
                      ? Sun
                      : Contrast
                "
                class="h-4 w-4"
              />
              <div>
                <div class="font-medium">{{ theme.label }}</div>
                <div class="text-xs text-muted-foreground">
                  {{ theme.description }}
                </div>
              </div>
            </div>
          </DropdownMenuRadioItem>
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>

    <!-- Font Size -->
    <Popover>
      <PopoverTrigger as-child>
        <Button
          variant="ghost"
          size="icon"
          class="h-6 w-6 rounded-none text-foreground hover:bg-accent hover:text-accent-foreground data-[state=open]:bg-accent data-[state=open]:text-accent-foreground"
          :aria-label="$t('problem.editor.fontSize')"
          :title="t('problem.editor.fontSettings')"
        >
          <Type class="h-3.5 w-3.5" aria-hidden="true" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" class="w-64">
        <div class="space-y-4">
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <label class="text-sm font-medium">{{
                t("problem.editor.fontSize")
              }}</label>
              <span class="text-sm text-muted-foreground">
                {{ fontSize[0] }}px
              </span>
            </div>
            <Slider
              v-model="fontSize"
              :min="10"
              :max="24"
              :step="1"
              class="w-full"
            />
          </div>

          <div class="space-y-2">
            <label class="text-sm font-medium">{{
              t("problem.editor.tabSize")
            }}</label>
            <div class="flex gap-1">
              <Button
                v-for="size in [2, 4, 8]"
                :key="size"
                :variant="tabSize === size ? 'default' : 'outline'"
                size="sm"
                class="flex-1 h-7"
                @click="tabSize = size"
              >
                {{ size }}
              </Button>
            </div>
          </div>

          <Button
            variant="outline"
            size="sm"
            class="w-full"
            :aria-label="$t('problem.editor.resetToDefaults')"
            @click="handleResetSettings"
          >
            <RotateCcw class="h-3.5 w-3.5 mr-1" aria-hidden="true" />
            {{ t("problem.editor.resetToDefaults") }}
          </Button>
        </div>
      </PopoverContent>
    </Popover>

    <!-- Templates -->
    <Button
      variant="ghost"
      size="icon"
      class="h-6 w-6 rounded-none text-foreground hover:bg-accent hover:text-accent-foreground"
      :aria-label="$t('problem.editor.codeTemplates')"
      :title="t('problem.editor.codeTemplates')"
      @click="showTemplates = true"
    >
      <Code2 class="h-3.5 w-3.5" aria-hidden="true" />
    </Button>

    <!-- Keyboard Shortcuts -->
    <Button
      variant="ghost"
      size="icon"
      class="h-6 w-6 rounded-none text-foreground hover:bg-accent hover:text-accent-foreground"
      :aria-label="$t('problem.shortcuts.title')"
      :title="t('problem.editor.keyboardShortcuts')"
      @click="showShortcuts = true"
    >
      <Keyboard class="h-3.5 w-3.5" aria-hidden="true" />
    </Button>

    <!-- Accessibility Settings -->
    <AccessibilitySettings />

    <!-- Modals -->
    <KeyboardShortcutsModal v-model:open="showShortcuts" />
    <CodeTemplatesModal
      v-model:open="showTemplates"
      :language="language"
      @insert="handleInsertTemplate"
    />
  </div>
</template>
