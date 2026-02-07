export interface Problem {
  id: number;
  title: string;
  slug: string;
  difficulty: "Easy" | "Medium" | "Hard";
  acceptance_rate: number;
  acceptanceRate?: number; // alias or computed
  tags: string[];
  status?: "solved" | "attempted" | "todo";
  isPremium?: boolean;
  hasSolution?: boolean;
  completedTime?: string; // Frontend specific
}
