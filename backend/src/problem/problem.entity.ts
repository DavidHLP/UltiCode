import { Entity, Column, PrimaryColumn, OneToMany, OneToOne } from 'typeorm';
import { ProblemDetail } from './problem-detail.entity';
import { ProblemTagRelation } from './problem-tag-relation.entity';
import { ProblemLanguage } from './problem-language.entity';
import { ProblemExample } from './problem-example.entity';

@Entity('problems')
export class Problem {
  @PrimaryColumn({ type: 'bigint' })
  id: number;

  @Column({ length: 120 })
  slug: string;

  @Column({ length: 255 })
  title: string;

  @Column({ type: 'enum', enum: ['Easy', 'Medium', 'Hard'] })
  difficulty: string;

  @Column({ type: 'decimal', precision: 5, scale: 2, default: 0 })
  acceptance_rate: number;

  @Column({
    type: 'enum',
    enum: ['solved', 'attempted', 'todo'],
    default: 'todo',
  })
  status: string;

  @Column({ default: false })
  is_premium: boolean;

  @Column({ default: false })
  has_solution: boolean;

  @Column({ type: 'date', nullable: true })
  completed_time: Date;

  @OneToOne(() => ProblemDetail, (detail) => detail.problem)
  detail: ProblemDetail;

  @OneToMany(() => ProblemTagRelation, (relation) => relation.problem)
  tagRelations: ProblemTagRelation[];

  @OneToMany(() => ProblemLanguage, (lang) => lang.problem)
  languages: ProblemLanguage[];

  @OneToMany(() => ProblemExample, (example) => example.problem)
  examples: ProblemExample[];
}
