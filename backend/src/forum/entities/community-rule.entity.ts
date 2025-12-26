import { Entity, Column, PrimaryColumn, ManyToOne, JoinColumn } from 'typeorm';
import { ForumCommunity } from './community.entity';

@Entity('forum_community_rules')
export class ForumCommunityRule {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ name: 'community_id', length: 40 })
  communityId: string;

  @Column({ length: 120 })
  title: string;

  @Column({ type: 'text' })
  body: string;

  @Column({ name: 'sort_order', default: 0 })
  sortOrder: number;

  @Column({ name: 'created_at' })
  createdAt: Date;

  @ManyToOne(() => ForumCommunity)
  @JoinColumn({ name: 'community_id' })
  community: ForumCommunity;
}
