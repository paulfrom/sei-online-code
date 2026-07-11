/**
 * Service layer for Memory Seed Template endpoints.
 * Mirrors backend MemorySeedTemplateApi; no MSW handlers are added.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}/api/memory/seed-templates`;

/**
 * @typedef {Object} MemorySeedTemplateDto
 * @property {string} id
 * @property {string} code
 * @property {string} name
 * @property {string} [description]
 * @property {number} version
 * @property {'DRAFT'|'ACTIVE'|'ARCHIVED'} status
 * @property {boolean} isDefault
 * @property {'BUILTIN'|'USER_CONFIG'} sourceType
 * @property {string} [projectMemoryTemplate]
 * @property {string} [memoryRulesTemplate]
 * @property {string} [decisionsTemplate]
 * @property {string} [modulesTemplate]
 * @property {string} [publishedAt]
 * @property {string} [archivedAt]
 */

/** @returns {Promise<import('@/services/onlineCode').ResultData<MemorySeedTemplateDto[]>>} */
export async function list() {
  return request({ url: API, method: 'GET' });
}

/** @returns {Promise<import('@/services/onlineCode').ResultData<MemorySeedTemplateDto>>} */
export async function activeDefault() {
  return request({ url: `${API}/active-default`, method: 'GET' });
}

/** @returns {Promise<import('@/services/onlineCode').ResultData<MemorySeedTemplateDto>>} */
export async function get(id) {
  return request({ url: `${API}/${id}`, method: 'GET' });
}

/** @returns {Promise<import('@/services/onlineCode').ResultData<MemorySeedTemplateDto>>} */
export async function saveDraft(dto) {
  const { id, ...body } = dto;
  if (id) {
    return request({ url: `${API}/${id}/save-draft`, method: 'POST', data: body });
  }
  return request({ url: API, method: 'POST', data: body });
}

/** @returns {Promise<import('@/services/onlineCode').ResultData<MemorySeedTemplateDto>>} */
export async function publish(id) {
  return request({ url: `${API}/${id}/publish`, method: 'POST' });
}

/** @returns {Promise<import('@/services/onlineCode').ResultData<MemorySeedTemplateDto>>} */
export async function setDefault(id) {
  return request({ url: `${API}/${id}/set-default`, method: 'POST' });
}

/** @returns {Promise<import('@/services/onlineCode').ResultData<MemorySeedTemplateDto>>} */
export async function archive(id) {
  return request({ url: `${API}/${id}/archive`, method: 'POST' });
}

/** @returns {Promise<import('@/services/onlineCode').ResultData<MemorySeedTemplateDto>>} */
export async function bootstrapDefault() {
  return request({ url: `${API}/bootstrap-default`, method: 'POST' });
}

/**
 * @param {string} [memorySeedTemplateId]
 * @returns {Promise<import('@/services/onlineCode').ResultData<MemorySeedTemplateDto>>}
 */
export async function resolve(memorySeedTemplateId) {
  return request({
    url: `${API}/resolve`,
    method: 'GET',
    params: { memorySeedTemplateId },
  });
}
