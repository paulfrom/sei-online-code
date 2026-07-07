/**
 * Requirement service.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}`;

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
