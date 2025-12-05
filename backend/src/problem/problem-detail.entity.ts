import { Entity, Column, PrimaryColumn, OneToOne, JoinColumn } from 'typeorm';
import { Problem } from './problem.entity';

interface ProblemCompany {
  id: string;
  name: string;
  logo?: string;
}

@Entity('problem_details')
export class ProblemDetail {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ type: 'bigint', unique: true })
  problem_id: number;

  @OneToOne(() => Problem, (problem) => problem.detail)
  @JoinColumn({ name: 'problem_id' })
  problem: Problem;

  @Column({ length: 120 })
  slug: string;

  @Column({ type: 'text' })
  summary: string;

  @Column({ type: 'json', nullable: true })
  companies: (ProblemCompany | string)[] | null;

  @Column({ default: 0 })
  likes: number;

  @Column({ default: 0 })
  dislikes: number;

  @Column({ type: 'decimal', precision: 5, scale: 1, default: 1500 })
  difficulty_rating: number;

  @Column()
  updated_at: Date;

  @Column({ type: 'text', nullable: true })
  follow_up: string;

  @Column({ type: 'json' })
  constraints_json: string[];

  @Column({ type: 'json', nullable: true })
  hints: string[] | null;
}
