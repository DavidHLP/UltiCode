import { Entity, Column, PrimaryColumn, ManyToOne, JoinColumn } from 'typeorm';
import { Problem } from './problem.entity';

@Entity('problem_examples')
export class ProblemExample {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ type: 'bigint' })
  problem_id: number;

  @ManyToOne(() => Problem, (problem) => problem.examples)
  @JoinColumn({ name: 'problem_id' })
  problem: Problem;

  @Column({ default: 0 })
  example_order: number;

  @Column({ type: 'text', name: 'input_text' })
  inputText: string;

  @Column({ type: 'text', name: 'output_text' })
  outputText: string;

  @Column({ type: 'text', nullable: true })
  explanation: string;

  @Column({ type: 'json', nullable: true })
  inputs: { name: string; value: string }[];
}
