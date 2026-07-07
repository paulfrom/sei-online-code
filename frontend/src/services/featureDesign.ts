/**
 * Service layer for FeatureDesign endpoints (API-CONTRACT-PRE-BUILD.md §5 P6-P11, P12a, P14).
 * All calls target the frozen contract and are served by MSW.
 */
import { request } from '@ead/suid-utils-react';
import { PROJECT_SERVER_PATH } from '@/utils/constants';
import type { Search, ResultData, PageResult } from './onlineCode';
import type { FailureInfoFields } from './plan';

const API = `${PROJECT_SERVER_PATH}`;

/** FeatureDesignStatus enum (contract §3.2) */
export type FeatureDesignStatus =
  | 'PENDING'
  | 'GENERATING'
  | 'DRAFT'
  | 'CONFIRMED'
  | 'STALE'
  | 'FAILED';

/** FeatureDesignBuildStatus enum (contract §3.3) */
export type FeatureDesignBuildStatus =
  | 'IDLE'
  | 'BUILDING'
  | 'BUILT'
  | 'BUILD_FAILED'
  | 'STALE';

/** FeatureDesignContent (contract §2.4) */
export interface FeatureDesignContent {
  featureId: string;
  goal: string;
  design: Record<string, any>;
  acceptance: string[];
  fileScope: string[];
}

/** FeatureDesignDto (contract §2.3) */
export interface FeatureDesignDto extends FailureInfoFields {
  id: string;
  projectId: string;
  featureId: string;
  version: number;
  status: FeatureDesignStatus;
  buildStatus: FeatureDesignBuildStatus;
  content?: FeatureDesignContent;
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

/** EditFeatureDesignRequest (contract §2.5) */
export interface EditFeatureDesignRequest {
  content: FeatureDesignContent;
}

/** RegenerateFeatureDesignRequest (contract §2.5) */
export interface RegenerateFeatureDesignRequest {
  modifyHint?: string;
}

/** ConfirmFeatureDesignsRequest (contract §2.5) */
export interface ConfirmFeatureDesignsRequest {
  ids: string[];
}

/** Store URL used directly by ExtTable remotePaging (contract P6). */
export const FEATURE_DESIGN_FIND_BY_PAGE_URL = `${API}/featureDesign/findByPage`;

/** #P6 find FeatureDesign by page */
export async function findByPage(search: Search) {
  return request({
    url: FEATURE_DESIGN_FIND_BY_PAGE_URL,
    method: 'POST',
    data: search,
  });
}

/** #P7 get latest FeatureDesign */
export async function getLatest(id: string) {
  return request({
    url: `${API}/featureDesign/${id}`,
    method: 'GET',
  });
}

/** #P8 edit FeatureDesign */
export async function edit(id: string, content: FeatureDesignContent) {
  return request({
    url: `${API}/featureDesign/${id}`,
    method: 'PUT',
    data: { content } as EditFeatureDesignRequest,
  });
}

/** #P9 regenerate FeatureDesign */
export async function regenerate(id: string, modifyHint?: string) {
  return request({
    url: `${API}/featureDesign/${id}/regenerate`,
    method: 'POST',
    data: { modifyHint } as RegenerateFeatureDesignRequest,
  });
}

/** #P10 batch confirm FeatureDesigns */
export async function confirm(ids: string[]) {
  return request({
    url: `${API}/featureDesign/confirm`,
    method: 'POST',
    data: { ids } as ConfirmFeatureDesignsRequest,
  });
}

/** #P11 confirm one FeatureDesign */
export async function confirmOne(id: string) {
  return request({
    url: `${API}/featureDesign/${id}/confirm`,
    method: 'POST',
  });
}

/** #P12a build one FeatureDesign */
export async function build(id: string) {
  return request({
    url: `${API}/featureDesign/${id}/build`,
    method: 'POST',
  });
}

/** #P14 get FeatureDesign history */
export async function history(id: string) {
  return request({
    url: `${API}/featureDesign/${id}/history`,
    method: 'GET',
  });
}

/** #P12 build project (all confirmed FeatureDesigns) */
export async function buildProject(projectId: string) {
  return request({
    url: `${API}/project/${projectId}/build`,
    method: 'POST',
  });
}
