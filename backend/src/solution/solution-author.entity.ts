import { Entity, Column, PrimaryColumn } from 'typeorm';

@Entity('solution_authors')
export class SolutionAuthor {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 120 })
  name: string;

  @Column({ length: 120 })
  role: string;

  @Column({ length: 16 })
  avatar_color: string;
}
