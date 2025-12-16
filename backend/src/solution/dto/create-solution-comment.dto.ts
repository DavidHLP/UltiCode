export class CreateSolutionCommentDto {
  content: string;
  parentId?: string;
  userId: string; // Typically extracted from auth context, but simplified here
}
