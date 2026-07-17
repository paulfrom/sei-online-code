/**
 * RunObservation service.
 *
 * Wraps the backend RunObservationApi (ADR-001 §9 / 计划 §3). Manual
 * observations are append-only — they never directly mutate step/effect
 * state; state changes go through controlled reconcile commands elsewhere.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}`;

/** Run observations, paginated (newest observedAt first). */
export async function findByRun(runId, page = 1, rows = 20) {
  return request({
    url: `${API}/runObservation/findByRun`,
    method: 'GET',
    params: { runId, page, rows },
  });
}

/** Append a manual observation (authorized users only; append-only). */
export async function appendManual(payload) {
  return request({
    url: `${API}/runObservation/appendManual`,
    method: 'POST',
    data: payload,
  });
}
