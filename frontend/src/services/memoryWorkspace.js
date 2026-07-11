/**
 * Service layer for Workspace Memory endpoints.
 * Mirrors backend MemoryWorkspaceApi; no MSW handlers are added.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}/api/memory`;

/**
 * @typedef {Object} WorkspaceMemoryDto
 * @property {string} id
 * @property {string} projectId
 * @property {number} version
 * @property {'CURRENT'|'ARCHIVED'|'FAILED'} status
 * @property {'FRESH'|'STALE_BY_SOURCE_CHANGE'|'STALE_BY_SPEC_CHANGE'|'STALE_BY_RULE_CHANGE'|'STALE_BY_PROJECT_MEMORY_CHANGE'|'PLATFORM_MEMORY_DRIFT'} freshness
 * @property {number} memorySpecVersion
 * @property {string} [memorySeedTemplateId]
 * @property {number} [agentMemorySeedVersion]
 * @property {string} [workspacePath]
 * @property {string} [agentMemoryFingerprint]
 * @property {string} [projectRuleFingerprint]
 * @property {string} [failureSummary]
 * @property {string} [failureDetail]
 * @property {string} generatedAt
 */

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
 * @property {number} retryCount
 * @property {number} maxRetryCount
 * @property {string} [nextRetryAt]
 * @property {string} [startedAt]
 * @property {string} [finishedAt]
 * @property {string} [failureSummary]
 * @property {string} [failureDetail]
 */

/** @returns {Promise<import('@/services/onlineCode').ResultData<WorkspaceMemoryDto>>} */
export async function current(projectId) {
  return request({ url: `${API}/workspace/current`, method: 'GET', params: { projectId } });
}

/** @returns {Promise<import('@/services/onlineCode').ResultData<WorkspaceMemoryDto[]>>} */
export async function history(projectId) {
  return request({ url: `${API}/workspace/history`, method: 'GET', params: { projectId } });
}

/** @returns {Promise<import('@/services/onlineCode').ResultData<MemoryJobDto>>} */
export async function initialize(projectId) {
  return request({ url: `${API}/workspace/initialize`, method: 'POST', params: { projectId } });
}

/** @returns {Promise<import('@/services/onlineCode').ResultData<MemoryJobDto>>} */
export async function rebuild(projectId) {
  return request({ url: `${API}/workspace/rebuild`, method: 'POST', params: { projectId } });
}
