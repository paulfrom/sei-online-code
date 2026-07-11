/**
 * Service layer for Memory Job endpoints.
 * Mirrors backend MemoryJobApi; no MSW handlers are added.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}/api/memory`;

/**
 * @typedef {Object} MemoryJobDto
 * @property {string} id
 * @property {string} projectId
 * @property {string} [requirementId]
 * @property {string} [codingTaskId]
 * @property {string} [runId]
 * @property {'MEMORY_INITIALIZE'|'MEMORY_REBUILD'|'MEMORY_REFRESH_BY_SOURCE_CHANGE'|'MEMORY_REFRESH_BY_PROJECT_MEMORY_CHANGE'|'MEMORY_REFRESH_BY_RULE_CHANGE'|'MEMORY_REFRESH_BY_SPEC_CHANGE'|'MEMORY_UPDATE_AFTER_CODING_TASK'|'MEMORY_VALIDATE'} jobType
 * @property {'PENDING'|'RUNNING'|'SUCCEEDED'|'FAILED'|'CANCELLED'} status
 * @property {string} triggerSource
 * @property {string} [newWorkspaceMemoryId]
 * @property {string} [baseWorkspaceMemoryId]
 * @property {string} idempotencyKey
 * @property {number} priority
 * @property {number} retryCount
 * @property {number} maxRetryCount
 * @property {string} [nextRetryAt]
 * @property {string} [startedAt]
 * @property {string} [finishedAt]
 * @property {string} [failureSummary]
 * @property {string} [failureDetail]
 */

/**
 * @param {string} projectId
 * @returns {Promise<import('@/services/onlineCode').ResultData<MemoryJobDto[]>>}
 */
export async function findByProject(projectId) {
  return request({ url: `${API}/jobs`, method: 'GET', params: { projectId } });
}

/**
 * @param {string} jobId
 * @returns {Promise<import('@/services/onlineCode').ResultData<MemoryJobDto>>}
 */
export async function retry(jobId) {
  return request({ url: `${API}/jobs/${jobId}/retry`, method: 'POST' });
}
