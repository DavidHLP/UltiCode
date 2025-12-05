import { Entity, Column, PrimaryColumn, ManyToOne, JoinColumn } from 'typeorm';
import { Problem } from './problem.entity';

@Entity('problem_languages')
export class ProblemLanguage {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ type: 'bigint' })
  problem_id: number;

  @ManyToOne(() => Problem, (problem) => problem.languages)
  @JoinColumn({ name: 'problem_id' })
  problem: Problem;

  @Column({ length: 50 })
  label: string;

  @Column({ length: 50 })
  value: string;

  @Column({ name: 'starter_code', type: 'text' })
  starterCode: string;
}
