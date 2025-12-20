import { IsNotEmpty, IsString } from 'class-validator';

export class SaveNoteDto {
  @IsString()
  @IsNotEmpty()
  content: string;
}
