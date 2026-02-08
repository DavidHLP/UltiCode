import { ref, type Ref } from "vue";

export interface ProblemLayoutState {
  isSidePanelOpen: Ref<boolean>;
  isNotesOpen: Ref<boolean>;
  toggleSidePanel: () => void;
  toggleNotes: () => void;
}

export function useProblemLayout(): ProblemLayoutState {
  const isSidePanelOpen = ref(false);
  const isNotesOpen = ref(false);

  const toggleSidePanel = () => {
    isSidePanelOpen.value = !isSidePanelOpen.value;
  };

  const toggleNotes = () => {
    isNotesOpen.value = !isNotesOpen.value;
  };

  return {
    isSidePanelOpen,
    isNotesOpen,
    toggleSidePanel,
    toggleNotes,
  };
}
