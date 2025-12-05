import { Entity, Column, PrimaryColumn } from 'typeorm';

@Entity('contest_participants')
export class ContestParticipant {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 40 })
  contest_id: string;

  @Column({ length: 40 })
  user_id: string;

  @Column({ length: 120 })
  username: string;

  @Column({ type: 'enum', enum: ['registered', 'participated', 'virtual'] })
  status: string;

  @Column()
  registered_at: Date;

  @Column({ nullable: true })
  started_at: Date;

  @Column({ nullable: true })
  finished_at: Date;

  @Column({ nullable: true })
  rank: number;

  @Column({ default: 0 })
  score: number;

  @Column({ nullable: true })
  finish_time_seconds: number;

  @Column({ default: false })
  is_virtual: boolean;
}
