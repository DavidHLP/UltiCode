import { inject } from "vue";
import { ProblemContextKey } from "./problem-context";

export function useProblemContext() {
  const context = inject(ProblemContextKey);
  if (!context) {
    throw new Error("ProblemContext is not provided");
  }
  return context;
}
