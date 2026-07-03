package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dto.plan.PlanFeature;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 规划智能体服务（桩，T13 实现）
 *
 * <p>职责：spawn 规划智能体和功能设计智能体。本类为桩，T13 补充完整实现。</p>
 */
@Service
public class PlanAgentService {

    public PlanAgentService() {
        // T13 补充依赖注入
    }

    /**
     * spawn 规划智能体
     *
     * @param projectId 项目 id
     * @param modifyHint 修改提示
     */
    public void spawnPlanning(String projectId, String modifyHint) {
        // TODO(T13): implement — ClaudeRunner + SkillMaterializer + 信号量 + D11 链式落库
    }

    /**
     * 批量 spawn 功能设计智能体
     *
     * @param projectId 项目 id
     * @param features 功能列表
     */
    public void spawnFeatureDesigns(String projectId, List<PlanFeature> features) {
        // TODO(T13): implement — ClaudeRunner + SkillMaterializer + 信号量 + D11 链式落库
    }
}
