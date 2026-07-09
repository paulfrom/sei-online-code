/**
 * Local panel props and shared types for the requirement workspace.
 */
import type {
  CodingTaskDto,
  DetailedDesignDto,
  OverviewDesignDto,
  RequirementDto,
  RunDto,
} from '@/services/onlineCodeTypes';

/** Tabs rendered by the workspace. */
export type WorkspaceTab = 'prd' | 'overview' | 'detailed' | 'coding' | 'runs';

/** Generic EADP ResultData<T> envelope returned by the JS service layer. */
export interface ResultData<T> {
  success: boolean;
  message: string | null;
  data: T | null;
}

/** Props every panel receives. */
export interface PanelSharedProps {
  loading?: boolean;
  onRefresh: () => void;
}

export interface PrdPanelProps extends PanelSharedProps {
  requirement: RequirementDto;
}

export interface OverviewDesignPanelProps extends PanelSharedProps {
  requirementId: string;
  overviewDesign: OverviewDesignDto | null;
}

export interface DetailedDesignPanelProps extends PanelSharedProps {
  detailedDesigns: DetailedDesignDto[];
}

export interface CodingTaskPanelProps extends PanelSharedProps {
  codingTasks: CodingTaskDto[];
  onViewRuns?: (task: CodingTaskDto) => void;
}

export interface RunHistoryPanelProps extends PanelSharedProps {
  runs: RunDto[];
}
