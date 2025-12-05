import { Entity, Column, PrimaryColumn, ManyToOne, JoinColumn } from 'typeorm';
import { ForumCommunity } from './forum-community.entity';
import { ForumUser } from './forum-user.entity';

@Entity('forum_posts')
export class ForumPost {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 40 })
  community_id: string;

  @ManyToOne(() => ForumCommunity)
  @JoinColumn({ name: 'community_id' })
  community: ForumCommunity;

  @Column({ length: 60 })
  user_id: string;

  @ManyToOne(() => ForumUser)
  @JoinColumn({ name: 'user_id' })
  author: ForumUser;

  @Column({ length: 255, nullable: true })
  permalink: string;

  @Column({ length: 255 })
  title: string;

  @Column({
    type: 'enum',
    enum: ['announcement', 'discussion', 'showcase', 'question', 'hiring'],
    nullable: true,
  })
  flair_type: string | null;

  @Column({ type: 'varchar', length: 60, nullable: true })
  flair_label: string | null;

  @Column({ type: 'json' })
  tags: string[];

  @Column({ type: 'text', nullable: true })
  excerpt: string;

  @Column({ type: 'json', nullable: true })
  media: Record<string, unknown>[] | null;

  @Column({ type: 'json', nullable: true })
  recommendation: { label?: string } | null;

  @Column({
    type: 'enum',
    enum: ['upvoted', 'downvoted', 'neutral'],
    default: 'neutral',
  })
  vote_state: string;

  @Column({ default: false })
  is_saved: boolean;

  @Column({ default: 0 })
  impressions: number;

  @Column({ default: false })
  is_pinned: boolean;

  @Column({ default: false })
  is_locked: boolean;

  @Column()
  created_at: Date;
}
