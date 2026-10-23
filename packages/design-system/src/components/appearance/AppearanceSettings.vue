<script setup lang="ts">
import { computed, ref, watch } from "vue";
import {
  APPEARANCE_OPTIONS,
  APPEARANCE_PRESETS,
  DEFAULT_APPEARANCE,
  useAppearance,
  type Appearance,
  type AppearanceKey,
} from "@ulticode/theme";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@ulticode/design-system/ui/dialog";
import {
  Field,
  FieldGroup,
  FieldLabel,
} from "@ulticode/design-system/ui/field";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@ulticode/design-system/ui/select";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@ulticode/design-system/ui/card";
import { Button } from "@ulticode/design-system/ui/button";
import { Input } from "@ulticode/design-system/ui/input";
import { Badge } from "@ulticode/design-system/ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@ulticode/design-system/ui/dropdown-menu";
import { Alert, AlertDescription } from "@ulticode/design-system/ui/alert";
import { Check, RotateCcw, ChevronDown } from "@lucide/vue";

const props = defineProps<{ locale?: string }>();
const zh = computed(() => (props.locale ?? "zh-CN").startsWith("zh"));
const {
  settingsOpen: open,
  settingsReturnFocus,
  saved,
  account,
  previewing,
  persistenceError,
  beginPreview,
  preview,
  cancel,
  apply,
} = useAppearance();
const draft = ref<Appearance>({ ...saved.value });
let returnFocus: HTMLElement | null = null;
const text = computed(() =>
  zh.value
    ? {
        title: "外观设置",
        description: "更改会实时预览。应用后保存到当前用户在此浏览器中的偏好。",
        preview: "实时预览",
        sample: "示例界面与图表，不代表真实业务数据。",
        apply: "应用",
        cancel: "取消",
        reset: "恢复默认",
        preset: "预设",
        default: "默认",
        focus: "专注",
        warm: "暖色",
        accent: "主题色",
        base: "基础色",
        chart: "图表配色",
        font: "字体",
        radius: "圆角",
        density: "界面密度",
        menu: "菜单样式",
        mode: "明暗模式",
        input: "示例输入框",
        action: "主要操作",
        status: "正常",
        menuPreview: "预览菜单",
        error:
          "未能保存外观设置。检查浏览器存储权限后重试；取消可恢复已保存外观。",
        chartLabel: "图表预览：系列 A 40，系列 B 70，系列 C 55。",
      }
    : {
        title: "Appearance",
        description:
          "Changes preview live. Apply saves preferences for this user in this browser.",
        preview: "Live preview",
        sample: "Sample interface and chart, not real business data.",
        apply: "Apply",
        cancel: "Cancel",
        reset: "Restore defaults",
        preset: "Preset",
        default: "Default",
        focus: "Focus",
        warm: "Warm",
        accent: "Theme color",
        base: "Base color",
        chart: "Chart palette",
        font: "Font",
        radius: "Radius",
        density: "Density",
        menu: "Menu style",
        mode: "Color mode",
        input: "Sample input",
        action: "Primary action",
        status: "Healthy",
        menuPreview: "Preview menu",
        error:
          "Could not save appearance. Check browser storage permissions and retry. Cancel restores saved preferences.",
        chartLabel: "Chart preview: series A 40, series B 70, series C 55.",
      },
);
const optionLabels = computed<Record<string, string>>(() =>
  zh.value
    ? {
        neutral: "中性",
        blue: "蓝色",
        violet: "紫色",
        emerald: "翠绿",
        orange: "橙色",
        zinc: "锌灰",
        slate: "蓝灰",
        stone: "暖灰",
        gray: "冷灰",
        default: "标准",
        contrast: "对比",
        system: "跟随系统",
        serif: "衬线",
        mono: "等宽",
        none: "无",
        small: "小",
        medium: "中",
        large: "大",
        comfortable: "舒适",
        compact: "紧凑",
        subtle: "柔和",
        solid: "实色",
        light: "浅色",
        dark: "深色",
      }
    : {
        neutral: "Neutral",
        blue: "Blue",
        violet: "Violet",
        emerald: "Emerald",
        orange: "Orange",
        zinc: "Zinc",
        slate: "Slate",
        stone: "Stone",
        gray: "Gray",
        default: "Standard",
        contrast: "Contrast",
        system: "System",
        serif: "Serif",
        mono: "Monospace",
        none: "None",
        small: "Small",
        medium: "Medium",
        large: "Large",
        comfortable: "Comfortable",
        compact: "Compact",
        subtle: "Subtle",
        solid: "Solid",
        light: "Light",
        dark: "Dark",
      },
);
const keys = Object.keys(APPEARANCE_OPTIONS) as AppearanceKey[];
watch(open, (value) => {
  if (value) {
    const active = document.activeElement;
    const triggerId =
      active instanceof HTMLElement && active.getAttribute("role") === "menuitem"
        ? active.closest('[role="menu"]')?.getAttribute("aria-labelledby")
        : null;
    returnFocus =
      settingsReturnFocus.value ??
      (triggerId ? document.getElementById(triggerId) : null) ??
      (active instanceof HTMLElement ? active : null);
    draft.value = beginPreview();
  } else {
    settingsReturnFocus.value = null;
    if (previewing.value) cancel();
  }
});
function restoreFocus(event: Event) {
  if (!returnFocus?.isConnected) return;
  event.preventDefault();
  returnFocus.focus({ preventScroll: true });
}
watch(account, () => {
  open.value = false;
});
function choose(key: AppearanceKey, value: unknown) {
  if (!(APPEARANCE_OPTIONS[key] as readonly unknown[]).includes(value)) return;
  Object.assign(draft.value, { [key]: value });
  preview(draft.value);
}
function preset(value: keyof typeof APPEARANCE_PRESETS) {
  draft.value = { ...APPEARANCE_PRESETS[value] };
  preview(draft.value);
}
function save(value = draft.value) {
  if (apply(value)) open.value = false;
}
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent
      class="w-[calc(100%-2rem)] max-h-[90dvh] overflow-y-auto sm:max-w-3xl"
      data-testid="appearance-dialog"
      @close-auto-focus="restoreFocus"
    >
      <DialogHeader>
        <DialogTitle>{{ text.title }}</DialogTitle>
        <DialogDescription>{{ text.description }}</DialogDescription>
      </DialogHeader>
      <div class="grid gap-6 md:grid-cols-2">
        <div class="flex flex-col gap-4">
          <Field>
            <FieldLabel>{{ text.preset }}</FieldLabel>
            <div class="flex flex-wrap gap-2">
              <Button
                v-for="(_, name) in APPEARANCE_PRESETS"
                :key="name"
                variant="outline"
                size="sm"
                @click="preset(name)"
                >{{ text[name] }}</Button
              >
            </div>
          </Field>
          <FieldGroup class="grid gap-4 sm:grid-cols-2">
            <Field v-for="key in keys" :key="key">
              <FieldLabel :for="`appearance-${key}`">{{
                text[key]
              }}</FieldLabel>
              <Select
                :model-value="draft[key]"
                @update:model-value="choose(key, $event)"
              >
                <SelectTrigger
                  :id="`appearance-${key}`"
                  class="w-full"
                  :data-testid="`appearance-${key}`"
                  ><SelectValue
                /></SelectTrigger>
                <SelectContent
                  ><SelectGroup>
                    <SelectItem
                      v-for="option in APPEARANCE_OPTIONS[key]"
                      :key="option"
                      :value="option"
                      >{{ optionLabels[option] }}</SelectItem
                    >
                  </SelectGroup></SelectContent
                >
              </Select>
            </Field>
          </FieldGroup>
        </div>
        <Card class="self-start" data-testid="appearance-preview">
          <CardHeader
            ><CardTitle>{{ text.preview }}</CardTitle
            ><CardDescription>{{ text.sample }}</CardDescription></CardHeader
          >
          <CardContent class="flex flex-col gap-4">
            <Field
              ><FieldLabel for="appearance-sample-input">{{
                text.input
              }}</FieldLabel
              ><Input id="appearance-sample-input" :placeholder="text.input"
            /></Field>
            <div class="flex flex-wrap items-center gap-2">
              <Button>{{ text.action }}</Button
              ><Badge variant="secondary"
                ><Check aria-hidden="true" />{{ text.status }}</Badge
              >
            </div>
            <DropdownMenu>
              <DropdownMenuTrigger as-child
                ><Button variant="outline"
                  >{{ text.menuPreview
                  }}<ChevronDown data-icon="inline-end" /></Button
              ></DropdownMenuTrigger>
              <DropdownMenuContent
                ><DropdownMenuGroup
                  ><DropdownMenuItem>{{ text.preview }}</DropdownMenuItem
                  ><DropdownMenuItem>{{
                    text.action
                  }}</DropdownMenuItem></DropdownMenuGroup
                ></DropdownMenuContent
              >
            </DropdownMenu>
            <figure class="flex flex-col gap-2" :aria-label="text.chartLabel">
              <div
                class="flex h-28 items-end gap-4 border-b px-4"
                role="img"
                :aria-label="text.chartLabel"
              >
                <div
                  v-for="(height, index) in [40, 70, 55]"
                  :key="index"
                  class="flex-1 rounded-t-sm border-2 border-card"
                  :style="{
                    height: `${height}%`,
                    background: `var(--chart-${index + 1})`,
                  }"
                  :title="`${String.fromCharCode(65 + index)}: ${height}`"
                />
              </div>
              <figcaption class="flex justify-around text-sm tabular-nums">
                <span>A: 40</span><span>B: 70</span><span>C: 55</span>
              </figcaption>
            </figure>
          </CardContent>
        </Card>
      </div>
      <Alert v-if="persistenceError" variant="destructive" role="alert"
        ><AlertDescription>{{ text.error }}</AlertDescription></Alert
      >
      <DialogFooter class="flex-wrap">
        <Button variant="outline" @click="save({ ...DEFAULT_APPEARANCE })"
          ><RotateCcw data-icon="inline-start" />{{ text.reset }}</Button
        >
        <Button
          variant="outline"
          data-testid="appearance-cancel"
          @click="open = false"
          >{{ text.cancel }}</Button
        >
        <Button data-testid="appearance-apply" @click="save()">{{
          text.apply
        }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
