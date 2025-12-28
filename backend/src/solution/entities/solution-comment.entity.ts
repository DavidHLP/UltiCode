import {
  Entity,
  Column,
  PrimaryColumn,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
} from 'typeorm';
import { SolutionMeta } from '../solution-meta.entity';
import { SolutionAuthor } from '../solution-author.entity';

@Entity('solution_comments')
export class SolutionComment {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ name: 'solution_id', length: 40 })
  solutionId: string;

  @ManyToOne(() => SolutionMeta)
  @JoinColumn({ name: 'solution_id' })
  solution: SolutionMeta;

  @Column({ name: 'parent_id', type: 'varchar', length: 40, nullable: true })
  parentId: string | null;

  @ManyToOne(() => SolutionComment)
  @JoinColumn({ name: 'parent_id' })
  parent: SolutionComment | null;

  @Column({ name: 'author_id', length: 40 })
  authorId: string;

  @ManyToOne(() => SolutionAuthor)
  @JoinColumn({ name: 'author_id' })
  author: SolutionAuthor;

  @Column({ type: 'text' })
  body: string;

  @Column({ default: 0 })
  upvotes: number;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}
