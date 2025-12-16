import { Entity, Column, PrimaryColumn } from 'typeorm';

@Entity('forum_communities')
export class ForumCommunity {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 120 })
  name: string;

  @Column({ length: 60 })
  slug: string;

  @Column({ type: 'text' })
  description: string;

  @Column({ default: 0 })
  members: number;

  @Column({ default: 0 })
  online: number;
}
