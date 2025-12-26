import { Entity, Column, PrimaryColumn, ManyToOne, JoinColumn } from 'typeorm';
import { ForumCommunity } from './community.entity';

@Entity('forum_community_links')
export class ForumCommunityLink {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ name: 'community_id', length: 40 })
  communityId: string;

  @Column({ length: 120 })
  label: string;

  @Column({ length: 255 })
  url: string;

  @Column({ type: 'text', nullable: true })
  description: string;

  @Column({ name: 'sort_order', default: 0 })
  sortOrder: number;

  @ManyToOne(() => ForumCommunity)
  @JoinColumn({ name: 'community_id' })
  community: ForumCommunity;
}
