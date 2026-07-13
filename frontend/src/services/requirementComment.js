/**
 * RequirementComment service.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}`;

export async function findCommentsByRequirement(requirementId) {
  return request({
    url: `${API}/requirementComment/requirement/${requirementId}`,
    method: 'GET',
  });
}