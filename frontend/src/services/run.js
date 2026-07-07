/**
 * Run service.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}`;

export async function findRunsByCodingTask(codingTaskId) {
  return request({ url: `${API}/run/findByCodingTask`, method: 'GET', params: { codingTaskId } });
}

export async function findOneRun(id) {
  return request({ url: `${API}/run/findOne`, method: 'GET', params: { id } });
}
