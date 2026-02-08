<script lang="ts">
import { h, defineComponent } from "vue";
import { useI18n } from "vue-i18n";
import { useProblemContext } from "../useProblemContext";

import DescriptionView from "@/views/problems/description/DescriptionView.vue";
import ProblemSolutionsView from "@/views/problems/solutions/ProblemSolutionsView.vue";
import SubmissionsView from "@/views/problems/submissions/SubmissionsView.vue";
import CodeView from "../code/CodeView.vue";
import TestCaseView from "../test/TestCaseView.vue";
import TestResultsView from "../test/TestResultsView.vue";

const { t } = useI18n();

export const ConnectedDescriptionView = defineComponent({
  setup() {
    const { problem } = useProblemContext();
    return () =>
      problem.value
        ? h(
            "div",
            { class: "px-1 py-2" },
            h(DescriptionView, { problem: problem.value }),
          )
        : h(
            "div",
            { class: "flex items-center justify-center h-full" },
            t("common.status.loading"),
          );
  },
});

export const ConnectedSolutionsView = defineComponent({
  setup() {
    const { problem } = useProblemContext();
    return () =>
      problem.value
        ? h(
            "div",
            { class: "px-1 py-2" },
            h(ProblemSolutionsView, {
              problemId: problem.value.id,
              followUp: problem.value.followUp ?? "",
            }),
          )
        : h(
            "div",
            { class: "flex items-center justify-center h-full" },
            t("common.status.loading"),
          );
  },
});

export const ConnectedSubmissionsView = defineComponent({
  setup() {
    const { problem, contestId } = useProblemContext();
    return () =>
      problem.value
        ? h(
            "div",
            { class: "px-1 py-2" },
            h(SubmissionsView, {
              problemId: problem.value.id,
              contestId: contestId.value ?? undefined,
            }),
          )
        : h(
            "div",
            { class: "flex items-center justify-center h-full" },
            t("common.status.loading"),
          );
  },
});

export const ConnectedCodeView = defineComponent({
  setup() {
    const { problem } = useProblemContext();
    return () =>
      problem.value && problem.value.languages.length
        ? h(CodeView, {
            key: problem.value.id,
            languages: problem.value.languages,
            starterNotes: problem.value.starterNotes ?? [],
          })
        : h(
            "div",
            { class: "flex items-center justify-center h-full" },
            t("common.status.loading"),
          );
  },
});

export const ConnectedTestCaseView = defineComponent({
  setup() {
    const { problem } = useProblemContext();
    return () =>
      problem.value
        ? h(
            "div",
            { class: "px-1 py-2" },
            h(TestCaseView, { testCases: problem.value.testCases ?? [] }),
          )
        : h(
            "div",
            { class: "flex items-center justify-center h-full" },
            t("common.status.loading"),
          );
  },
});

export const ConnectedTestResultsView = defineComponent({
  setup() {
    const { runResult } = useProblemContext();
    return () =>
      h(
        "div",
        { class: "px-1 py-2" },
        h(TestResultsView, { runResult: runResult.value }),
      );
  },
});

// Default export for Vite compatibility (this component has no template)
export default defineComponent({
  setup() {
    return () => null;
  },
});
</script>
