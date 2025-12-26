import { Entity, Column, PrimaryColumn, ManyToOne, JoinColumn } from 'typeorm';
import { ForumCommunity } from './community.entity';

@Entity('forum_community_members')
export class ForumCommunityMember {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ name: 'community_id', length: 40 })
  communityId: string;

  @Column({ name: 'user_id', length: 40 })
  userId: string;

  @Column({
    type: 'enum',
    enum: ['OWNER', 'MODERATOR', 'MEMBER'],
    default: 'MEMBER',
  })
  role: string;

  @Column({ name: 'joined_at' })
  joinedAt: Date;

  @ManyToOne(() => ForumCommunity)
  @JoinColumn({ name: 'community_id' })
  community: ForumCommunity;
}
