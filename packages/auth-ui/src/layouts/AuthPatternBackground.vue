<script setup lang="ts">
/**
 * AuthPatternBackground - Right-side grid + terminal spec panel
 *
 * The "wall of code" pattern that fills the right half of the auth page on
 * desktop. The headline + subtitle are passed as props (i18n strings from
 * the consumer). The bottom `system_status.sh` block uses the `spec` prop
 * so each app can show its own port + test counts without forking the
 * layout.
 *
 * On screens narrower than 1024px the AuthGrid collapses (display:none)
 * and this panel is hidden. Mobile users see only the form side.
 */
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@ulticode/design-system/ui/card";
import AuthGrid from "../components/AuthGrid.vue";
import type { AuthPatternLine } from "./useAuthLayout";

defineOptions({
  name: "AuthPatternBackground",
});

withDefaults(
  defineProps<{
    /** Pre-title prefix rendered in code font (e.g. "$"). Defaults to "$". */
    prefix?: string;
    /** Main headline (i18n string). Supports newlines via `whitespace-pre-line`. */
    title: string;
    /** Subheadline (i18n string). */
    subtitle?: string;
    /** Status window title shown in the panel header. Defaults to "system_status.sh". */
    windowTitle?: string;
    /** Custom status lines; falls back to a generic placeholder if empty. */
    spec?: AuthPatternLine[];
  }>(),
  {
    prefix: "",
    subtitle: "",
    windowTitle: "UltiCode",
    spec: () => [],
  },
);

function outputClass(
  tone: "normal" | "success" | "accent" | "muted" | undefined,
): string {
  switch (tone) {
    case "success":
      return "text-status-success-mark font-bold";
    case "accent":
      return "text-primary font-bold";
    case "muted":
      return "text-foreground dark:text-muted-foreground";
    default:
      return "text-muted-foreground dark:text-muted-foreground";
  }
}

function renderOutput(
  line:
    string | { text: string; tone?: "normal" | "success" | "accent" | "muted" },
) {
  return typeof line === "string"
    ? { text: line, tone: "normal" as const }
    : line;
}
</script>

<template>
  <AuthGrid>
    <div class="flex max-w-lg flex-col gap-6">
      <h2 class="whitespace-pre-line text-3xl font-semibold tracking-tight">
        <span v-if="prefix" class="font-mono">{{ prefix }}</span> {{ title }}
      </h2>
      <p v-if="subtitle" class="text-muted-foreground">{{ subtitle }}</p>
      <Card v-if="spec.length">
        <CardHeader
          ><CardTitle>{{ windowTitle }}</CardTitle></CardHeader
        >
        <CardContent class="flex flex-col gap-3">
          <template v-for="(line, index) in spec" :key="index">
            <p v-if="line.prompt" class="text-sm text-muted-foreground">
              {{ prefix }} {{ line.prompt }}
            </p>
            <p :class="outputClass(renderOutput(line.output).tone)">
              {{ renderOutput(line.output).text }}
            </p>
          </template>
        </CardContent>
      </Card>
    </div>
  </AuthGrid>
</template>
