import { Entity, Column, PrimaryColumn } from 'typeorm';

@Entity('forum_communities')
export class ForumCommunity {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 120 })
  name: string;

  @Column({ length: 60, unique: true })
  slug: string;

  @Column({ type: 'text' })
  description: string;

  // Visual Customization
  @Column({ length: 255, nullable: true })
  icon: string;

  @Column({ length: 20, nullable: true })
  color: string;

  @Column({ length: 255, nullable: true })
  banner: string;

  // Stats (cached for performance)
  @Column({ default: 0 })
  members: number;

  @Column({ default: 0 })
  online: number;

  @Column({ name: 'posts_count', default: 0 })
  postsCount: number;

  @Column({ name: 'posts_today', default: 0 })
  postsToday: number;

  @Column({ name: 'posts_week', default: 0 })
  postsWeek: number;

  // Metadata
  @Column({ name: 'is_official', default: false })
  isOfficial: boolean;

  @Column({ name: 'is_featured', default: false })
  isFeatured: boolean;

  @Column({ name: 'sort_order', default: 0 })
  sortOrder: number;

  @Column({ name: 'created_at' })
  createdAt: Date;

  // Access Control
  @Column({
    type: 'enum',
    enum: ['PUBLIC', 'RESTRICTED', 'PRIVATE'],
    default: 'PUBLIC',
  })
  visibility: string;
}
