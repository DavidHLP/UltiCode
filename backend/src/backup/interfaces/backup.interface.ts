export type BackupType = 'FULL' | 'PARTIAL';
export type BackupStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';

export interface BackupInfo {
  id: string;
  filename: string;
  size: bigint;
  type: BackupType;
  status: BackupStatus;
  created_by: string;
  created_at: Date;
  completed_at: Date | null;
  error: string | null;
  metadata: Record<string, unknown> | null;
}

export interface BackupProgress {
  id: string;
  status: BackupStatus;
  progress: number;
  tables_completed: number;
  tables_total: number;
  error?: string;
}

export interface RestoreProgress {
  id: string;
  status: BackupStatus;
  progress: number;
  tables_completed: number;
  tables_total: number;
  error?: string;
}
