import { Injectable } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { ForumPost } from './forum-post.entity';
import { ForumCommunity } from './forum-community.entity';
import { ForumComment } from './forum-comment.entity';

@Injectable()
export class ForumService {
  constructor(
    @InjectRepository(ForumPost)
    private postsRepository: Repository<ForumPost>,
    @InjectRepository(ForumCommunity)
    private communitiesRepository: Repository<ForumCommunity>,
    @InjectRepository(ForumComment)
    private commentsRepository: Repository<ForumComment>,
  ) {}

  async findAllPosts(): Promise<ForumPost[]> {
    return this.postsRepository.find({
      relations: ['author', 'community'],
      order: { createdAt: 'DESC' },
    });
  }

  async findOnePost(id: string): Promise<ForumPost | null> {
    return this.postsRepository.findOne({
      where: { id },
      relations: ['author', 'community'],
    });
  }

  async getThread(
    id: string,
  ): Promise<(ForumPost & { comments: ForumComment[] }) | null> {
    const post = await this.postsRepository.findOne({
      where: { id },
      relations: ['author', 'community'],
    });
    if (post) {
      const comments = await this.commentsRepository.find({
        where: { postId: id },
        relations: ['author'],
        order: { createdAt: 'ASC' },
      });
      return { ...post, comments };
    }

    // Fallback to seed data if database is empty
    return null;
  }

  async findAllCommunities(): Promise<ForumCommunity[]> {
    return this.communitiesRepository.find();
  }

  async createComment(
    postId: string,
    body: string,
    parentId: string | null,
  ): Promise<ForumComment> {
    const comment = this.commentsRepository.create({
      id: randomUUID(),
      postId,
      body,
      parentId,
      authorId: 'shadcn', // Default to Shadcn username
      createdAt: new Date(),
      upvotes: 0,
    });

    return this.commentsRepository.save(comment);
  }
}
