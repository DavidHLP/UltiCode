import { ref } from "vue";

export function useProblemPanels() {
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
