export interface DifficultyStats {
  count: number;
  total: number;
}

export interface ProblemStats {
  Easy: DifficultyStats;
  Medium: DifficultyStats;
  Hard: DifficultyStats;
}

export interface HeatmapPoint {
  date: string;
  level: number;
}

export interface UserStats {
  stats: ProblemStats;
  streak: number;
  totalSolved: number;
  heatmap: HeatmapPoint[];
}

export interface UserSkill {
  tagName: string;
  tagSlug: string;
  count: number;
}

export interface UserSkills {
  skills: UserSkill[];
  totalSolved: number;
}

export interface RecentActivity {
  id: string;
  type: "submission" | "solution" | "post" | "comment";
  title: string;
  status?: string;
  createdAt: string;
  link?: string;
}
