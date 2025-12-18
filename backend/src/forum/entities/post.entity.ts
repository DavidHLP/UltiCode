import { Entity, Column, PrimaryColumn, ManyToOne, JoinColumn } from 'typeorm';
import { ForumCommunity } from './community.entity';
import { ForumUser } from './user.entity';

@Entity('forum_posts')
export class ForumPost {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ name: 'community_id', length: 40 })
  communityId: string;

  @ManyToOne(() => ForumCommunity)
  @JoinColumn({ name: 'community_id' })
  community: ForumCommunity;

  @Column({ name: 'user_id', length: 60 })
  userId: string;

  @ManyToOne(() => ForumUser)
  @JoinColumn({ name: 'user_id' })
  author: ForumUser;

  @Column({ length: 255, nullable: true })
  permalink: string;

  @Column({ length: 255 })
  title: string;

  @Column({
    name: 'flair_type',
    type: 'enum',
    enum: ['announcement', 'discussion', 'showcase', 'question', 'hiring'],
    nullable: true,
  })
  flairType: string | null;

  @Column({ name: 'flair_label', type: 'varchar', length: 60, nullable: true })
  flairLabel: string | null;

  @Column({ type: 'json' })
  tags: string[];

  @Column({ type: 'text', nullable: true })
  excerpt: string;

  @Column({ type: 'json', nullable: true })
  media: Record<string, unknown>[] | null;

  @Column({ type: 'json', nullable: true })
  recommendation: { label?: string } | null;

  @Column({
    name: 'vote_state',
    type: 'enum',
    enum: ['upvoted', 'downvoted', 'neutral'],
    default: 'neutral',
  })
  voteState: string;

  @Column({ name: 'is_saved', default: false })
  isSaved: boolean;

  @Column({ default: 0 })
  impressions: number;

  @Column({ default: 0 })
  views: number;

  @Column({ name: 'is_pinned', default: false })
  isPinned: boolean;

  @Column({ name: 'is_locked', default: false })
  isLocked: boolean;

  @Column({ name: 'created_at' })
  createdAt: Date;

  @Column({ type: 'json', nullable: true })
  stats: {
    views?: number;

    comments?: number;
    saves?: number;
    shares?: number;
    upvote_ratio?: number;
    awards?: number;
  } | null;
}
