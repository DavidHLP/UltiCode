export interface Problem {
  id: number;
  title: string;
  slug: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  acceptance_rate: number;
  acceptanceRate?: number;
  tags: string[];
  status?: "solved" | "attempted" | "todo";
  isPremium?: boolean;
  hasSolution?: boolean;
  completedTime?: string;
  sortOrder?: number;
  addedAt?: string;
}
