import { Entity, Column, PrimaryColumn, OneToMany } from 'typeorm';
import { ContestProblem } from './contest-problem.entity';

@Entity('contests')
export class Contest {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 120 })
  title: string;

  @Column({ length: 120 })
  slug: string;

  @Column({ type: 'enum', enum: ['weekly', 'biweekly', 'special'] })
  contest_type: string;

  @Column()
  start_time: Date;

  @Column()
  duration_minutes: number;

  @Column({ type: 'enum', enum: ['upcoming', 'running', 'finished'] })
  status: string;

  @Column({ default: 0 })
  registered_count: number;

  @Column({ default: 0 })
  participant_count: number;

  @Column({ default: true })
  is_rated: boolean;

  @Column({ type: 'text', nullable: true })
  description: string;

  @Column({ length: 255, nullable: true })
  cover_image: string;

  @OneToMany(() => ContestProblem, (cp) => cp.contest)
  problems: ContestProblem[];
}
