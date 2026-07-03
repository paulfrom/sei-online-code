package com.changhong.onlinecode.service;

import com.changhong.onlinecode.agent.ClaudeRunner;
import com.changhong.onlinecode.agent.SkillMaterializer;
import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dao.PlanDao;
import com.changhong.onlinecode.dao.SkillDao;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.dto.featuredesign.FeatureDesignContent;
import com.changhong.onlinecode.dto.plan.PlanContent;
import com.changhong.onlinecode.dto.plan.PlanFeature;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.entity.Plan;
import com.changhong.onlinecode.entity.Project;
import com.changhong.onlinecode.entity.Skill;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * 规划/设计智能体 spawn 编排（T13）。复用 {@link ClaudeRunner} + {@link SkillMaterializer}。
 *
 * <p>D11 链式落库：{@code ClaudeRunner.execute} 返 {@link CompletableFuture}，{@code .thenApply} 解析 JSON
 * → {@code .thenAccept} 持久化 DRAFT+content，{@code .exceptionally} 落 FAILED。无独立 onRunFinished 钩子。</p>
 *
 * <p><b>与计划 C8 的偏差</b>：计划称 spawn 时若 latest 已 GENERATING 则拒绝/跳过，但 T9.regenerate/T10.regenerate
 * 调用前已预置 GENERATING 行——若 spawn 再拒则永拒。故并发守卫归调用方（T9 B5 / T10 regenerate BUILDING 拒绝），
 * 本类直接处理 latest 行。bounded concurrency 信号量仍在此处（spawnFeatureDesigns）。</p>
 *
 * @author sei-online-code
 */
@Service
public class PlanAgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanAgentService.class);
    private static final int MAX_CONCURRENT_FD = 4;

    private final PlanDao planDao;
    private final FeatureDesignDao featureDesignDao;
    private final AgentService agentService;
    private final SkillDao skillDao;
    private final ProjectService projectService;
    private final ClaudeRunner claudeRunner;
    private final SkillMaterializer skillMaterializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Semaphore fdPermits = new Semaphore(MAX_CONCURRENT_FD);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public PlanAgentService(PlanDao planDao, FeatureDesignDao featureDesignDao, AgentService agentService,
                            SkillDao skillDao, ProjectService projectService, ClaudeRunner claudeRunner,
                            SkillMaterializer skillMaterializer) {
        this.planDao = planDao;
        this.featureDesignDao = featureDesignDao;
        this.agentService = agentService;
        this.skillDao = skillDao;
        this.projectService = projectService;
        this.claudeRunner = claudeRunner;
        this.skillMaterializer = skillMaterializer;
    }

    /**
     * spawn 规划智能体（latest Plan 应已由 caller 置 GENERATING）。D11 链式落库 DRAFT/FAILED。
     */
    public void spawnPlanning(String projectId, String modifyHint) {
        Plan plan = planDao.findLatestByProjectId(projectId);
        if (plan == null) {
            LOGGER.warn("spawnPlanning: no Plan for projectId={}, skip", projectId);
            return;
        }
        Agent agent = agentService.findByName("planning-agent");
        Project project = projectService.findOne(projectId);
        String prompt = buildPlanningPrompt(project, modifyHint);
        Path workdir = materializeSkills(agent);

        String iterationId = projectId; // 规划阶段无 Run，用 projectId 作日志键
        CompletableFuture<String> future = claudeRunner.execute(iterationId, prompt, workdir.toString());
        future.thenApply(json -> parseJson(json, PlanContent.class))
                .thenAccept(content -> {
                    plan.setContent(content);
                    plan.setStatus(PlanStatus.DRAFT);
                    planDao.save(plan);
                })
                .exceptionally(e -> {
                    LOGGER.error("spawnPlanning failed projectId={}", projectId, e);
                    plan.setStatus(PlanStatus.FAILED);
                    planDao.save(plan);
                    return null;
                });
    }

    /**
     * 批量 spawn 功能设计智能体（bounded concurrency，单条失败不影响其他，E3）。fire-and-forget。
     */
    public void spawnFeatureDesigns(String projectId, List<PlanFeature> features) {
        if (features == null || features.isEmpty()) {
            return;
        }
        for (PlanFeature f : features) {
            CompletableFuture.runAsync(() -> {
                try {
                    fdPermits.acquire();
                    spawnFeatureDesign(projectId, f.getFeatureId(), null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    fdPermits.release();
                }
            }, executor);
        }
    }

    /**
     * spawn 单个功能设计智能体（latest FD 应已由 caller 建）。D11 链式落库 DRAFT/FAILED。
     */
    public void spawnFeatureDesign(String projectId, String featureId, String modifyHint) {
        FeatureDesign fd = featureDesignDao.findLatestByProjectId(projectId).stream()
                .filter(x -> featureId.equals(x.getFeatureId()))
                .findFirst().orElse(null);
        if (fd == null) {
            LOGGER.warn("spawnFeatureDesign: no FD for projectId={} featureId={}, skip", projectId, featureId);
            return;
        }
        fd.setStatus(FeatureDesignStatus.GENERATING);
        featureDesignDao.save(fd);

        Agent agent = agentService.findByName("feature-design-agent");
        Plan plan = planDao.findLatestByProjectId(projectId);
        String prompt = buildFeatureDesignPrompt(plan, featureId, modifyHint);
        Path workdir = materializeSkills(agent);

        String iterationId = projectId + ":" + featureId;
        CompletableFuture<String> future = claudeRunner.execute(iterationId, prompt, workdir.toString());
        future.thenApply(json -> parseJson(json, FeatureDesignContent.class))
                .thenAccept(content -> {
                    fd.setContent(content);
                    fd.setStatus(FeatureDesignStatus.DRAFT);
                    featureDesignDao.save(fd);
                })
                .exceptionally(e -> {
                    LOGGER.error("spawnFeatureDesign failed projectId={} featureId={}", projectId, featureId, e);
                    fd.setStatus(FeatureDesignStatus.FAILED);
                    featureDesignDao.save(fd);
                    return null;
                });
    }

    private <T> T parseJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("parse " + type.getSimpleName() + " failed", e);
        }
    }

    private Path materializeSkills(Agent agent) {
        try {
            Path tmp = Files.createTempDirectory("agent-skills-");
            List<SkillMaterializer.SkillPayload> payloads = new ArrayList<>();
            if (agent != null && agent.getSkillIds() != null) {
                for (String sid : agent.getSkillIds()) {
                    Skill s = skillDao.findOne(sid);
                    if (s != null) {
                        payloads.add(new SkillMaterializer.SkillPayload(s.getName(), s.getContent(), s.getComputedHash()));
                    }
                }
            }
            skillMaterializer.materialize(tmp.toString(), payloads);
            return tmp;
        } catch (Exception e) {
            LOGGER.warn("materializeSkills failed, fallback to tmp root", e);
            return Path.of(System.getProperty("java.io.tmpdir"));
        }
    }

    private String buildPlanningPrompt(Project project, String modifyHint) {
        String desc = project == null ? "" : project.getDesign();
        String hint = modifyHint == null ? "" : modifyHint;
        return "项目描述：" + desc + "\n修改提示：" + hint
                + "\n输出 Plan JSON 骨架：summary/techAssumptions/features[]{featureId,title,outline}/nonGoals";
    }

    private String buildFeatureDesignPrompt(Plan plan, String featureId, String modifyHint) {
        String hint = modifyHint == null ? "" : modifyHint;
        return "规划书：" + (plan == null ? "" : plan.getContent())
                + "\nfeatureId：" + featureId + "\n修改提示：" + hint
                + "\n输出 FeatureDesign JSON 骨架：featureId/goal/design/acceptance[]/fileScope";
    }
}
