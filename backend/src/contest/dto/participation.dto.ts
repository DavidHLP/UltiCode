import { IsString, IsOptional, MaxLength } from 'class-validator';

export class RegisterContestDto {
  @IsString()
  @MaxLength(40)
  contest_id: string;
}

export class StartVirtualContestDto {
  @IsString()
  @MaxLength(40)
  contest_id: string;
}

export class SubmitContestProblemDto {
  @IsString()
  @MaxLength(40)
  contest_problem_id: string;

  @IsString()
  @MaxLength(50)
  language: string;

  @IsString()
  code: string;

  @IsOptional()
  @IsString()
  @MaxLength(40)
  virtual_session_id?: string;
}

export interface ParticipationStatus {
  isRegistered: boolean;
  status: string | null;
  participantId: string | null;
  virtualSessionId: string | null;
  startedAt: Date | null;
  finishedAt: Date | null;
  totalScore: number;
  totalPenalty: number;
}
