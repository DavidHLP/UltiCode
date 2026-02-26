import { IsString, IsOptional, IsInt, Min, IsEnum } from 'class-validator';
import { Transform } from 'class-transformer';

export enum SearchIndex {
  PROBLEMS = 'problems',
  USERS = 'users',
  POSTS = 'posts',
  SOLUTIONS = 'solutions',
}

export class SearchQueryDto {
  @IsString()
  query: string;

  @IsEnum(SearchIndex)
  @IsOptional()
  index?: SearchIndex;

  @IsInt()
  @Min(1)
  @Transform(({ value }) => parseInt(value, 10) || 1)
  @IsOptional()
  page?: number = 1;

  @IsInt()
  @Min(1)
  @Transform(({ value }) => parseInt(value, 10) || 20)
  @IsOptional()
  limit?: number = 20;
}

export interface SearchResult {
  id: string;
  type: SearchIndex;
  title: string;
  description?: string;
  url: string;
  highlights?: Record<string, string[]>;
}

export interface SearchResponse {
  query: string;
  total: number;
  page: number;
  limit: number;
  results: SearchResult[];
  facets?: Record<string, Record<string, number>>;
}
