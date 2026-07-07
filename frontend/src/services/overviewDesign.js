/**
 * OverviewDesign service.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}`;

export async function findOneOverviewDesign(requirementId) {
  return request({ url: `${API}/overview-design/findOne`, method: 'GET', params: { requirementId } });
}

export async function regenerateOverviewDesign(id, prompt) {
  return request({ url: `${API}/overview-design/${id}/regenerate`, method: 'POST', data: { prompt } });
}

export async function editOverviewDesign(id, content) {
  return request({ url: `${API}/overview-design/${id}/edit`, method: 'POST', data: { content } });
}

export async function confirmOverviewDesign(id) {
  return request({ url: `${API}/overview-design/${id}/confirm`, method: 'POST' });
}
