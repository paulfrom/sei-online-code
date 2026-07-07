/**
 * CodingTask service.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}`;

export async function findOneCodingTask(id) {
  return request({ url: `${API}/coding-task/findOne`, method: 'GET', params: { id } });
}

export async function findCodingTasksByPage(search) {
  return request({ url: `${API}/coding-task/findByPage`, method: 'POST', data: search });
}

export async function runCodingTask(id, userPrompt) {
  return request({ url: `${API}/coding-task/${id}/run`, method: 'POST', data: { userPrompt } });
}

export async function rerunCodingTask(id, rerunPrompt) {
  return request({ url: `${API}/coding-task/${id}/rerun`, method: 'POST', data: { rerunPrompt } });
}

export async function cancelCodingTask(id) {
  return request({ url: `${API}/coding-task/${id}/cancel`, method: 'POST' });
}
