import { IsEmail, IsNotEmpty, MinLength, IsOptional } from 'class-validator';

export class RegisterDto {
  @IsNotEmpty()
  username!: string;

  @IsEmail()
  email!: string;

  @IsOptional()
  @MinLength(6)
  password?: string;

  @IsOptional()
  avatar?: string;
}
