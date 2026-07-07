/**
 * DetailedDesign service.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}`;

export async function findOneDetailedDesign(id) {
  return request({ url: `${API}/detailed-design/findOne`, method: 'GET', params: { id } });
}

export async function findDetailedDesignsByOverview(overviewDesignId) {
  return request({ url: `${API}/detailed-design/findByOverview`, method: 'GET', params: { overviewDesignId } });
}

export async function regenerateDetailedDesign(id, prompt) {
  return request({ url: `${API}/detailed-design/${id}/regenerate`, method: 'POST', data: { prompt } });
}

export async function editDetailedDesign(id, content) {
  return request({ url: `${API}/detailed-design/${id}/edit`, method: 'POST', data: { content } });
}

export async function confirmDetailedDesign(id) {
  return request({ url: `${API}/detailed-design/${id}/confirm`, method: 'POST' });
}

export async function batchConfirmDetailedDesign(ids) {
  return request({ url: `${API}/detailed-design/batchConfirm`, method: 'POST', data: { ids } });
}
