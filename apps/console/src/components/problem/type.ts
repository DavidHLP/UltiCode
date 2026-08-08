import type { Problem } from "@/types/problem";
import type { Component } from "vue";

export type DisplayedProblem = Problem & {
  statusIcon?: Component;
};

export interface ProblemExplorerProps {
  problems?: Problem[];
  initialCategory?: string;
  editable?: boolean;
}

export interface ProblemTableProps {
  displayedProblems: DisplayedProblem[];
  numProblemsToShow: number;
  totalFilteredProblems: number;
  editable?: boolean;
}
