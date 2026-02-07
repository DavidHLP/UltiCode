import { defineStore } from "pinia";
import { ref } from "vue";

export const useProblemEditorStore = defineStore("problemEditor", () => {
  const code = ref("");
  const language = ref("typescript");

  const setCode = (next: string) => {
    code.value = next;
  };

  const setLanguage = (next: string) => {
    language.value = next;
  };

  return {
    code,
    language,
    setCode,
    setLanguage,
  };
});
