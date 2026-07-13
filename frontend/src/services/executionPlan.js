/**
 * ExecutionPlan service.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}`;

export async function findPlansByRequirement(requirementId) {
  return request({
    url: `${API}/executionPlan/requirement/${requirementId}`,
    method: 'GET',
  });
}

export async function findLatestPlanByRequirement(requirementId) {
  return request({
    url: `${API}/executionPlan/requirement/${requirementId}/latest`,
    method: 'GET',
  });
}