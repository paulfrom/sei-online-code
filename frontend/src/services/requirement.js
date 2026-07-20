/**
 * Requirement service.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}`;

/** Store URL for the requirement list ExtTable remotePaging. */
export const REQUIREMENT_FIND_BY_PAGE_URL = `${API}/requirement/findByPage`;

export async function saveRequirement(params) {
  return request({ url: `${API}/requirement/save`, method: 'POST', data: params });
}

export async function findOneRequirement(id) {
  return request({ url: `${API}/requirement/findOne`, method: 'GET', params: { id } });
}

export async function findRequirementsByPage(search) {
  return request({ url: `${API}/requirement/findByPage`, method: 'POST', data: search });
}

export async function regeneratePrd(id, prompt) {
  return request({ url: `${API}/requirement/${id}/regeneratePrd`, method: 'POST', data: { prompt } });
}

export async function editPrd(id, prdContent) {
  return request({ url: `${API}/requirement/${id}/editPrd`, method: 'POST', data: { prdContent } });
}

export async function confirmPrd(id) {
  return request({ url: `${API}/requirement/${id}/confirmPrd`, method: 'POST' });
}

export async function addRequirementComment(id, { content, metadataJson }) {
  return request({
    url: `${API}/requirement/${id}/comments`,
    method: 'POST',
    data: { content, metadataJson },
  });
}

export async function retryMr(id) {
  return request({ url: `${API}/requirement/${id}/mr/retry`, method: 'POST' });
}

export async function resumeRequirementAutomation(id) {
  return request({ url: `${API}/requirement/${id}/resume`, method: 'POST' });
}

export async function stopRequirementAutomation(id) {
  return request({ url: `${API}/requirement/${id}/stop`, method: 'POST' });
}
