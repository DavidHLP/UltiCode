import { Entity, Column, PrimaryColumn } from 'typeorm';

@Entity('solution_topics')
export class SolutionTopic {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 120 })
  name: string;

  @Column({ length: 120 })
  slug: string;

  @Column({ length: 120, nullable: true })
  name_translated: string;
}
