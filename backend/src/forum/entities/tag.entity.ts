import { Entity, Column, PrimaryColumn } from 'typeorm';

@Entity('forum_tags')
export class ForumTag {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 60, unique: true })
  name: string;

  @Column({ length: 60, unique: true })
  slug: string;

  @Column({ type: 'text', nullable: true })
  description: string;

  @Column({ length: 20, nullable: true })
  color: string;

  @Column({ name: 'usage_count', default: 0 })
  usageCount: number;

  @Column({ name: 'created_at' })
  createdAt: Date;
}
