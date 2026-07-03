/**
 * Service layer for Plan endpoints (API-CONTRACT-PRE-BUILD.md §5 P2-P5, P13).
 * All calls target the frozen contract and are served by MSW.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';

const API = `${PROJECT_SERVER_PATH}`;

/** PlanStatus enum (contract §3.1) */
export type PlanStatus = 'GENERATING' | 'DRAFT' | 'CONFIRMED' | 'FAILED';

/** PlanFeature — part of PlanContent (contract §2.2) */
export interface PlanFeature {
  featureId: string;
  title: string;
  outline: string;
}

/** PlanContent (contract §2.2) */
export interface PlanContent {
  summary: string;
  techAssumptions: string[];
  features: PlanFeature[];
  nonGoals: string[];
}

/** PlanDto (contract §2.1) */
export interface PlanDto {
  id: string;
  projectId: string;
  version: number;
  status: PlanStatus;
  content: PlanContent;
  modifyHint?: string;
  isLatest: boolean;
  creatorId?: string;
  creatorAccount?: string;
  creatorName?: string;
  createdDate?: string;
  lastEditorId?: string;
  lastEditorAccount?: string;
  lastEditorName?: string;
  lastEditedDate?: string;
}

/** EditPlanRequest (contract §2.5) */
export interface EditPlanRequest {
  content: PlanContent;
}

/** RegeneratePlanRequest (contract §2.5) */
export interface RegeneratePlanRequest {
  modifyHint?: string;
}

/** #P2 get latest Plan */
export async function getLatest(projectId: string) {
  return request({
    url: `${API}/plan/${projectId}`,
    method: 'GET',
  });
}

/** #P3 edit Plan */
export async function edit(projectId: string, content: PlanContent) {
  return request({
    url: `${API}/plan/${projectId}`,
    method: 'PUT',
    data: { content } as EditPlanRequest,
  });
}

/** #P4 regenerate Plan */
export async function regenerate(projectId: string, modifyHint?: string) {
  return request({
    url: `${API}/plan/${projectId}/regenerate`,
    method: 'POST',
    data: { modifyHint } as RegeneratePlanRequest,
  });
}

/** #P5 confirm Plan */
export async function confirm(projectId: string) {
  return request({
    url: `${API}/plan/${projectId}/confirm`,
    method: 'POST',
  });
}

/** #P13 get Plan history */
export async function history(projectId: string) {
  return request({
    url: `${API}/plan/${projectId}/history`,
    method: 'GET',
  });
}
