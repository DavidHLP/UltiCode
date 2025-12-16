import { IsInt, IsNotEmpty, IsString, IsIn } from 'class-validator';

export class VoteSolutionDto {
  @IsNotEmpty()
  @IsString()
  userId: string; // Ideally this comes from Auth token, but explicit here for now

  @IsInt()
  @IsIn([1, -1, 0]) // 0 can be for removing vote, or just use 1/-1 and handle toggle in service
  voteType: number;
}
