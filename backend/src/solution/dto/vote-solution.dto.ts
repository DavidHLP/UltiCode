import { IsIn, IsInt } from 'class-validator';

export class VoteSolutionDto {
  @IsInt()
  @IsIn([1, -1, 0]) // 0 can be for removing vote, or just use 1/-1 and handle toggle in service
  voteType: number;
}
