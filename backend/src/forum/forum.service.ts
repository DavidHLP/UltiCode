import { Injectable } from '@nestjs/common';
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
      order: { created_at: 'DESC' },
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
        where: { post_id: id },
        relations: ['author'],
        order: { created_at: 'ASC' },
      });
      return { ...post, comments };
    }

    // Fallback to seed data if database is empty
    return null;
  }

  async findAllCommunities(): Promise<ForumCommunity[]> {
    return this.communitiesRepository.find();
  }

  async getTrendingPosts(): Promise<ForumPost[]> {
    return this.postsRepository.find({
      relations: ['author', 'community'],
      order: { impressions: 'DESC' },
      take: 5,
    });
  }
}
