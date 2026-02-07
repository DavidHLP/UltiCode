export type SubmissionStatusKey =
  | "Accepted"
  | "Wrong Answer"
  | "Time Limit Exceeded"
  | "Memory Limit Exceeded"
  | "Output Limit Exceeded"
  | "Runtime Error"
  | "Compile Error"
  | "Presentation Error"
  | "System Error"
  | "Judging"
  | "Pending";

export interface SubmissionStatusMeta {
  key: SubmissionStatusKey;
  code: string;
  label: string;
  description?: string;
  suggestion?: string;
  category: "success" | "error" | "warning" | "pending" | "system";
  severity: "success" | "error" | "warning" | "info";
  isTerminal: boolean;
  sortOrder: number;
}

export interface SubmissionTestRecord {
  id: string;
  status: SubmissionStatusKey;
  runtime: number;
  memory: number;
}

export interface ContestSubmissionInfo {
  time_from_start: number;
  problem_index: string;
  score: number;
  is_accepted: boolean;
}

export interface SubmissionRecord {
  id: string;
  problem_id: number;
  status: SubmissionStatusKey;
  language: string;
  runtime: number;
  memory: number;
  compiler_error?: string;
  errorDetail?: string;
  input?: string;
  output?: string;
  expected_output?: string;
  created_at: string;
  submittedAt?: string; // alias
  notes?: string;
  code?: string;
  runtimeDistBinsMs?: { min: number; max: number; count: number }[];
  runtimeDist?: { distribution: [number, number][] };
  runtimePercentile?: number;
  memoryPercentile?: number;
  tests?: SubmissionTestRecord[];
  user?: {
    id: string;
    username: string;
    avatar?: string;
  };
  problem?: {
    id: number;
    title: string;
    slug: string;
  };
  contest_info?: ContestSubmissionInfo;
}
