import { Entity, Column, PrimaryColumn, ManyToOne, JoinColumn } from 'typeorm';
import { Contest } from './contest.entity';

@Entity('contest_problems')
export class ContestProblem {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 40 })
  contest_id: string;

  @ManyToOne(() => Contest, (contest) => contest.problems)
  @JoinColumn({ name: 'contest_id' })
  contest: Contest;

  @Column({ type: 'bigint' })
  problem_id: number;

  @Column({ length: 10 })
  problem_index: string;

  @Column({ default: 0 })
  score: number;

  @Column({ default: 0 })
  solved_count: number;

  @Column({ default: 0 })
  submission_count: number;
}
