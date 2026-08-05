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
  | "Sandbox Error"
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
  problemId?: number; // alias
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
  runtimeDistBinsMs?: DistributionBin[];
  runtimeDist?: { distribution: [number, number][] };
  runtimePercentile?: number;
  memoryPercentile?: number;
  memoryDistBinsMb?: DistributionBin[];
  tests?: SubmissionTestRecord[];
  user?: {
    id: string;
    username: string;
    name?: string;
    avatar?: string;
  };
  problem?: {
    id: number;
    title: string;
    slug: string;
  };
  contest_info?: ContestSubmissionInfo;
}

export type DistributionBin =
  | number
  | [number, number]
  | {
      min?: number;
      max?: number;
      value?: number;
      bin?: number;
      count?: number;
    };
