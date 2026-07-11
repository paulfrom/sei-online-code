/**
 * Service layer for Requirement Design Context endpoints.
 * Mirrors backend MemoryRequirementContextApi; no MSW handlers are added.
 * Placeholder for Phase 3 integration.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}/api/memory/requirement-context`;

/**
 * @typedef {Object} RequirementDesignContextDto
 * @property {string} id
 * @property {string} projectId
 * @property {string} requirementId
 * @property {string} [workspaceMemoryId]
 * @property {number} version
 * @property {'CURRENT'|'ARCHIVED'|'FAILED'} status
 * @property {'READY'|'STALE'|'FAILED'} contextStatus
 * @property {string} [requirementFingerprint]
 * @property {string} [requirementRelatedSnapshotJson]
 * @property {string} [requirementConflictReportJson]
 * @property {string} [designBasis]
 * @property {string} [validationResultJson]
 * @property {string} [sourceFingerprintsJson]
 * @property {string} [failureSummary]
 * @property {string} [failureDetail]
 * @property {string} generatedAt
 */

/**
 * @param {string} requirementId
 * @returns {Promise<import('@/services/onlineCode').ResultData<RequirementDesignContextDto>>}
 */
export async function current(requirementId) {
  return request({ url: `${API}/current`, method: 'GET', params: { requirementId } });
}

/**
 * @param {string} requirementId
 * @returns {Promise<import('@/services/onlineCode').ResultData<RequirementDesignContextDto>>}
 */
export async function prepare(requirementId) {
  return request({ url: `${API}/prepare`, method: 'POST', params: { requirementId } });
}
