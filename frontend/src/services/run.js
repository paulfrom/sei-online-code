/**
 * Run service.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}`;

export async function findRunsByCodingTask(codingTaskId) {
  return request({ url: `${API}/run/findByCodingTask`, method: 'GET', params: { codingTaskId } });
}

export async function findRunsByRequirement(requirementId) {
  return request({ url: `${API}/run/findByRequirement`, method: 'GET', params: { requirementId } });
}

export async function findOneRun(id) {
  return request({ url: `${API}/run/findOne`, method: 'GET', params: { id } });
}

export async function findRunUsage(runId) {
  return request({ url: `${API}/run/findUsage`, method: 'GET', params: { runId } });
}

export async function findRunEvidence(runId) {
  return request({ url: `${API}/run/findEvidence`, method: 'GET', params: { runId } });
}

export async function findRunLogFrames(runId, afterSequence = 0, limit = 2000) {
  return request({
    url: `${API}/run/findLogFrames`,
    method: 'GET',
    params: { runId, afterSequence, limit },
  });
}

export async function findRunArtifactContent(artifactId) {
  return request({
    url: `${API}/run/findArtifactContent`,
    method: 'GET',
    params: { artifactId },
  });
}

export async function findRunFeedback(runId) {
  return request({ url: `${API}/run/findFeedback`, method: 'GET', params: { runId } });
}

export async function findAppliedBehaviorMemories(runId) {
  return request({
    url: `${API}/run/findAppliedBehaviorMemories`,
    method: 'GET',
    params: { runId },
  });
}
