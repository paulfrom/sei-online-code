package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dao.PlanDao;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.entity.Plan;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 编码前项目状态实时聚合（D12）。职责仅"编码前状态聚合"，与 FeatureDesign 编码执行边界隔离。
 *
 * <p>契约 §4.1 聚合规则（含 D7 FAILED / D15 空集→DESIGNING）。状态不持久化，按最新 Plan + 最新 FeatureDesign
 * 实时计算。</p>
 *
 * @author sei-online-code
 */
@Service
public class ProjectStateService {

    private final PlanDao planDao;
    private final FeatureDesignDao featureDesignDao;

    public ProjectStateService(PlanDao planDao, FeatureDesignDao featureDesignDao) {
        this.planDao = planDao;
        this.featureDesignDao = featureDesignDao;
    }

    /**
     * 解析项目编码前状态（实时聚合，不持久化）。
     *
     * <ul>
     *   <li>无 Plan → {@code DRAFTING}</li>
     *   <li>Plan = FAILED → {@code FAILED}（D7）</li>
     *   <li>Plan ∈ {GENERATING, DRAFT} → {@code PLANNING}</li>
     *   <li>Plan = CONFIRMED 且任一 FD = FAILED → {@code FAILED}（D7）</li>
     *   <li>Plan = CONFIRMED 且 FD 空集 → {@code DESIGNING}（D15，视为未就绪）</li>
     *   <li>Plan = CONFIRMED 且全部 FD = CONFIRMED → {@code READY_TO_BUILD}</li>
     *   <li>否则（部分 FD 未确认）→ {@code DESIGNING}</li>
     * </ul>
     *
     * @param projectId 项目 id
     * @return DRAFTING / PLANNING / DESIGNING / READY_TO_BUILD / FAILED
     */
    public String resolvePreBuildState(String projectId) {
        Plan plan = planDao.findLatestByProjectId(projectId);
        if (plan == null) {
            return "DRAFTING";
        }
        if (plan.getStatus() == PlanStatus.FAILED) {
            return "FAILED";
        }
        if (plan.getStatus() == PlanStatus.GENERATING || plan.getStatus() == PlanStatus.DRAFT) {
            return "PLANNING";
        }
        // plan = CONFIRMED
        List<FeatureDesign> fds = featureDesignDao.findLatestByProjectId(projectId);
        if (fds.stream().anyMatch(f -> f.getStatus() == FeatureDesignStatus.FAILED)) {
            return "FAILED";
        }
        if (fds.isEmpty()) {
            return "DESIGNING";
        }
        boolean allConfirmed = fds.stream().allMatch(f -> f.getStatus() == FeatureDesignStatus.CONFIRMED);
        return allConfirmed ? "READY_TO_BUILD" : "DESIGNING";
    }
}
