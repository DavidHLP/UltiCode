import {
  Entity,
  Column,
  PrimaryColumn,
  ManyToOne,
  ManyToMany,
  JoinTable,
  JoinColumn,
} from 'typeorm';
import { SolutionAuthor } from './solution-author.entity';
import { SolutionBadge } from './solution-badge.entity';

@Entity('solution_metas')
export class SolutionMeta {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 255 })
  highlight: string;

  @Column({ length: 60, nullable: true })
  flair: string;

  @Column({ length: 40 })
  author_id: string;

  @ManyToOne(() => SolutionAuthor)
  @JoinColumn({ name: 'author_id' })
  author: SolutionAuthor;

  @Column({ length: 40 })
  views_text: string;

  @Column({ default: 0 })
  likes: number;

  @Column({ default: 0 })
  comments: number;

  @Column()
  created_at: Date;

  @Column()
  published_at: Date;

  @Column({ length: 60 })
  topic: string;

  @Column({ length: 40 })
  language_filter: string;

  @Column({ default: 0 })
  score: number;

  @ManyToMany(() => SolutionBadge, (badge) => badge.solutions)
  @JoinTable({
    name: 'solution_badge_relations',
    joinColumn: { name: 'meta_id', referencedColumnName: 'id' },
    inverseJoinColumn: { name: 'badge_id', referencedColumnName: 'id' },
  })
  badges: SolutionBadge[];
}
