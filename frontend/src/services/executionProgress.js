/**
 * ExecutionProgress service.
 *
 * Wraps the backend ExecutionProgressApi (ADR-001 §11 / 计划 §3) — the
 * authoritative aggregated snapshot of requirement execution progress.
 * The frontend must read canonical status from here, never infer it from
 * summaries, logs, exit codes or MR comments.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}`;

/** Requirement progress overview — authoritative server snapshot. */
export async function findOverview(requirementId) {
  return request({
    url: `${API}/executionProgress/findOverview`,
    method: 'GET',
    params: { requirementId },
  });
}

/** Execution step list. */
export async function findSteps(executionId) {
  return request({
    url: `${API}/executionProgress/findSteps`,
    method: 'GET',
    params: { executionId },
  });
}

/** Step checkpoints, paginated (newest sequence first). */
export async function findCheckpoints(stepId, page = 1, rows = 20) {
  return request({
    url: `${API}/executionProgress/findCheckpoints`,
    method: 'GET',
    params: { stepId, page, rows },
  });
}

/** Execution effects, paginated (newest preparedAt first). status optional. */
export async function findEffects(executionId, { status, page = 1, rows = 20 } = {}) {
  return request({
    url: `${API}/executionProgress/findEffects`,
    method: 'GET',
    params: { executionId, status, page, rows },
  });
}
