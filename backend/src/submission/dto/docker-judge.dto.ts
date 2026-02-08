/**
 * Docker Judge Data Transfer Objects
 *
 * Defines the data structures for communication between
 * the backend and the Docker container judge service.
 */

import Docker from 'dockerode';
import { JudgeTestCase, JudgeResult } from '../judge.service';

/**
 * Request payload for code execution in Docker container
 */
export interface DockerExecuteRequest {
  /** The source code to execute */
  code: string;
  /** Programming language (javascript, typescript) */
  language: string;
  /** Test cases to run against the code */
  testCases: JudgeTestCase[];
  /** Optional time limit in milliseconds (default: 2000) */
  timeLimit?: number;
  /** Optional memory limit in MB (default: 256) */
  memoryLimit?: number;
}

/**
 * Response from Docker container execution
 */
export interface DockerExecuteResponse {
  /** Whether execution was successful (not whether tests passed) */
  success: boolean;
  /** Judge result if execution succeeded */
  result?: JudgeResult;
  /** Error message if execution failed */
  error?: string;
}

/**
 * Health check response from container
 */
export interface ContainerHealthResponse {
  status: 'healthy' | 'unhealthy';
  timestamp: number;
}

/**
 * Container configuration options
 */
export interface ContainerConfig {
  /** Docker image to use */
  image: string;
  /** Memory limit in MB */
  memoryLimit: number;
  /** CPU limit (0.5 = 50% of one core) */
  cpuLimit: number;
  /** Maximum number of processes */
  pidsLimit: number;
  /** Execution timeout in milliseconds */
  timeout: number;
  /** Whether network is disabled */
  networkDisabled: boolean;
  /** Path to seccomp profile */
  seccompProfile?: string;
}

/**
 * Managed container state
 */
export interface ManagedContainer {
  /** Container ID */
  id: string;
  /** Docker container instance */
  container: Docker.Container;
  /** Whether container is currently in use */
  inUse: boolean;
  /** Time when container was created */
  createdAt: Date;
  /** Time of last execution */
  lastUsedAt?: Date;
  /** Number of executions performed */
  executionCount: number;
}

/**
 * Container pool statistics
 */
export interface ContainerPoolStats {
  /** Total containers in pool */
  totalContainers: number;
  /** Containers currently in use */
  activeContainers: number;
  /** Available containers */
  availableContainers: number;
  /** Total executions performed */
  totalExecutions: number;
  /** Average execution time in ms */
  avgExecutionTime: number;
  /** Pool utilization percentage */
  utilizationRate: number;
}

/**
 * Container execution metrics
 */
export interface ExecutionMetrics {
  /** Container ID used */
  containerId: string;
  /** Time to acquire container in ms */
  acquireTime: number;
  /** Execution time in ms */
  executionTime: number;
  /** Time to release container in ms */
  releaseTime: number;
  /** Total time in ms */
  totalTime: number;
  /** Peak memory usage in MB */
  peakMemory: number;
  /** Whether execution was successful */
  success: boolean;
  /** Error if failed */
  error?: string;
}
