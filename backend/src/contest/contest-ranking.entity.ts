import { Entity, Column, PrimaryColumn } from 'typeorm';

@Entity('contest_rankings')
export class ContestRanking {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 40 })
  contest_id: string;

  @Column({ length: 40 })
  user_id: string;

  @Column({ length: 120 })
  username: string;

  @Column()
  rank: number;

  @Column()
  score: number;

  @Column()
  finish_time_seconds: number;

  @Column({ nullable: true })
  q1_time_seconds: number;

  @Column({ nullable: true })
  q2_time_seconds: number;

  @Column({ nullable: true })
  q3_time_seconds: number;

  @Column({ nullable: true })
  q4_time_seconds: number;

  @Column()
  rating_before: number;

  @Column()
  rating_after: number;

  @Column()
  rating_change: number;

  @Column({ length: 10, default: 'CN' })
  country: string;
}
