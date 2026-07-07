package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.FeatureDesignDao;
import com.changhong.onlinecode.dao.PlanDao;
import com.changhong.onlinecode.dao.SpecDao;
import com.changhong.onlinecode.dto.FeatureDesignBuildResultDto;
import com.changhong.onlinecode.dto.enums.FailureCode;
import com.changhong.onlinecode.dto.enums.FailureStage;
import com.changhong.onlinecode.dto.enums.FeatureDesignBuildStatus;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.dto.enums.PlanStatus;
import com.changhong.onlinecode.dto.enums.SpecState;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.dto.plan.PlanFeature;
import com.changhong.onlinecode.dto.plan.PlanModule;
import com.changhong.onlinecode.entity.FeatureDesign;
import com.changhong.onlinecode.entity.Plan;
import com.changhong.onlinecode.entity.Spec;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 编码前补偿服务。
 */
@Service
public class CompensationService {

    private final PlanDao planDao;
    private final SpecDao specDao;
    private final FeatureDesignDao featureDesignDao;
    private final PlanService planService;
    private final SpecService specService;
    private final PlanAgentService planAgentService;
    private final SpecAgentService specAgentService;
    private final FeatureDesignBuildService featureDesignBuildService;
    private final FailureInfoSupport failureInfoSupport;
    private final CompensationLogService compensationLogService;

    @Value("${onlinecode.compensation.auto-build-enabled:true}")
    private boolean autoBuildEnabled;

    @Value("${onlinecode.compensation.build-timeout-minutes:30}")
    private long buildTimeoutMinutes;

    public CompensationService(PlanDao planDao,
                               SpecDao specDao,
                               FeatureDesignDao featureDesignDao,
                               PlanService planService,
                               SpecService specService,
                               PlanAgentService planAgentService,
                               SpecAgentService specAgentService,
                               FeatureDesignBuildService featureDesignBuildService,
                               FailureInfoSupport failureInfoSupport,
                               CompensationLogService compensationLogService) {
        this.planDao = planDao;
        this.specDao = specDao;
        this.featureDesignDao = featureDesignDao;
        this.planService = planService;
        this.specService = specService;
        this.planAgentService = planAgentService;
        this.specAgentService = specAgentService;
        this.featureDesignBuildService = featureDesignBuildService;
        this.failureInfoSupport = failureInfoSupport;
        this.compensationLogService = compensationLogService;
    }

    public void runCycle() {
        compensateFailedPlans();
        compensateMissingSpecs();
        compensateFailedSpecs();
        compensateMissingFeatureDesigns();
        compensateFailedFeatureDesigns();
        if (autoBuildEnabled) {
            compensateMissingBuilds();
            compensateFailedBuilds();
            timeoutBuildingFeatureDesigns();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void compensateFailedPlans() {
        Date now = new Date();
        for (Plan plan : planDao.findByStatusAndIsLatestTrue(PlanStatus.FAILED)) {
            if (!failureInfoSupport.canRetry(plan, now)) {
                continue;
            }
            failureInfoSupport.markRetrying(plan, TriggerSource.SCHEDULED_COMPENSATION, now);
            plan.setStatus(PlanStatus.GENERATING);
            planDao.save(plan);
            compensationLogService.record("PLAN", plan.getId(), "RETRY_PLAN", true,
                    "补偿重试概要设计", plan.getFailureSummary(), TriggerSource.SCHEDULED_COMPENSATION);
            planAgentService.spawnPlanning(plan.getProjectId(), retryHint(plan.getFailureSummary()),
                    TriggerSource.SCHEDULED_COMPENSATION);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void compensateMissingSpecs() {
        for (Plan plan : planDao.findByStatusAndIsLatestTrue(PlanStatus.CONFIRMED)) {
            List<PlanModule> modules = planService.modulesOrFallback(plan.getContent());
            if (modules.isEmpty()) {
                continue;
            }
            List<Spec> existingSpecs = specDao.findByProjectId(plan.getProjectId());
            int nextVersion = nextSpecVersion(existingSpecs);
            for (PlanModule module : modules) {
                if (hasSpecForModule(existingSpecs, module)) {
                    continue;
                }
                Spec spec = new Spec();
                spec.setProjectId(plan.getProjectId());
                spec.setVersion(nextVersion++);
                spec.setState(SpecState.GENERATING);
                spec.setModuleId(module.getModuleId());
                spec.setModuleTitle(module.getTitle());
                spec.setModuleSummary(module.getSummary());
                spec.setLastTriggerSource(TriggerSource.CHAIN_COMPENSATION);
                failureInfoSupport.clearSpecFailure(spec);
                Spec saved = specDao.save(spec);
                compensationLogService.record("PLAN", plan.getId(), "FILL_MISSING_SPEC", true,
                        "补齐详细设计", null, TriggerSource.CHAIN_COMPENSATION);
                specAgentService.spawnRequirement(plan.getProjectId(), null, saved.getId(),
                        TriggerSource.CHAIN_COMPENSATION);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void compensateFailedSpecs() {
        Date now = new Date();
        for (Spec spec : specDao.findByStateOrderByCreatedDateAsc(SpecState.FAILED)) {
            if (!failureInfoSupport.canRetry(spec, now)) {
                continue;
            }
            failureInfoSupport.markRetrying(spec, TriggerSource.SCHEDULED_COMPENSATION, now);
            spec.setState(SpecState.GENERATING);
            specDao.save(spec);
            compensationLogService.record("SPEC", spec.getId(), "RETRY_SPEC", true,
                    "补偿重试详细设计", spec.getFailureSummary(), TriggerSource.SCHEDULED_COMPENSATION);
            specAgentService.spawnRequirement(spec.getProjectId(), retryHint(spec.getFailureSummary()), spec.getId(),
                    TriggerSource.SCHEDULED_COMPENSATION);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void compensateMissingFeatureDesigns() {
        List<Spec> confirmedSpecs = specDao.findByStateOrderByCreatedDateAsc(SpecState.CONFIRMED);
        for (Spec spec : confirmedSpecs) {
            List<PlanFeature> expected = specService.featuresForModule(spec);
            if (expected.isEmpty()) {
                continue;
            }
            Set<String> existingFeatureIds = featureDesignDao.findLatestByProjectId(spec.getProjectId()).stream()
                    .map(FeatureDesign::getFeatureId)
                    .collect(Collectors.toSet());
            List<PlanFeature> missing = expected.stream()
                    .filter(feature -> !existingFeatureIds.contains(feature.getFeatureId()))
                    .collect(Collectors.toList());
            if (missing.isEmpty()) {
                continue;
            }
            compensationLogService.record("SPEC", spec.getId(), "FILL_MISSING_FEATURE_DESIGN", true,
                    "补齐功能设计", "missing=" + missing.size(), TriggerSource.CHAIN_COMPENSATION);
            planAgentService.spawnFeatureDesigns(spec.getProjectId(), missing, TriggerSource.CHAIN_COMPENSATION);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void compensateFailedFeatureDesigns() {
        Date now = new Date();
        for (FeatureDesign design : featureDesignDao.findByStatusAndIsLatestTrue(FeatureDesignStatus.FAILED)) {
            if (!failureInfoSupport.canRetry(design, now) || !hasConfirmedSpecForFeature(design)) {
                continue;
            }
            failureInfoSupport.markRetrying(design, TriggerSource.SCHEDULED_COMPENSATION, now);
            design.setStatus(FeatureDesignStatus.GENERATING);
            featureDesignDao.save(design);
            compensationLogService.record("FEATURE_DESIGN", design.getId(), "RETRY_FEATURE_DESIGN", true,
                    "补偿重试功能设计", design.getFailureSummary(), TriggerSource.SCHEDULED_COMPENSATION);
            planAgentService.spawnFeatureDesign(design.getProjectId(), design.getFeatureId(),
                    retryHint(design.getFailureSummary()), TriggerSource.SCHEDULED_COMPENSATION);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void compensateMissingBuilds() {
        for (FeatureDesign design : featureDesignDao.findByStatusAndIsLatestTrue(FeatureDesignStatus.CONFIRMED)) {
            if (design.getBuildStatus() != FeatureDesignBuildStatus.IDLE) {
                continue;
            }
            OperateResultWithData<FeatureDesignBuildResultDto> result =
                    featureDesignBuildService.build(design.getId(), TriggerSource.CHAIN_COMPENSATION);
            compensationLogService.record("FEATURE_DESIGN", design.getId(), "AUTO_BUILD_MISSING", result.successful(),
                    result.successful() ? "自动补齐编码执行" : result.getMessage(),
                    result.successful() ? null : design.getFailureSummary(),
                    TriggerSource.CHAIN_COMPENSATION);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void compensateFailedBuilds() {
        Date now = new Date();
        for (FeatureDesign design : featureDesignDao.findByBuildStatusAndIsLatestTrue(FeatureDesignBuildStatus.BUILD_FAILED)) {
            if (design.getStatus() != FeatureDesignStatus.CONFIRMED || !failureInfoSupport.canRetry(design, now)) {
                continue;
            }
            failureInfoSupport.markRetrying(design, TriggerSource.SCHEDULED_COMPENSATION, now);
            featureDesignDao.save(design);
            OperateResultWithData<FeatureDesignBuildResultDto> result =
                    featureDesignBuildService.build(design.getId(), TriggerSource.SCHEDULED_COMPENSATION);
            compensationLogService.record("FEATURE_DESIGN", design.getId(), "RETRY_BUILD", result.successful(),
                    result.successful() ? "补偿重试编码执行" : result.getMessage(),
                    result.successful() ? null : design.getFailureSummary(),
                    TriggerSource.SCHEDULED_COMPENSATION);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void timeoutBuildingFeatureDesigns() {
        Date cutoff = new Date(System.currentTimeMillis() - buildTimeoutMinutes * 60_000L);
        for (FeatureDesign design : featureDesignDao.findByBuildStatusAndIsLatestTrue(FeatureDesignBuildStatus.BUILDING)) {
            Date edited = design.getLastEditedDate() != null ? design.getLastEditedDate() : design.getCreatedDate();
            if (edited == null || !edited.before(cutoff)) {
                continue;
            }
            design.setBuildStatus(FeatureDesignBuildStatus.BUILD_FAILED);
            failureInfoSupport.markFeatureDesignFailure(design, FailureCode.BUILD_TIMEOUT, FailureStage.BUILD,
                    "编码执行超时", "BUILDING 超过超时时间未收口", TriggerSource.SCHEDULED_COMPENSATION, new Date());
            featureDesignDao.save(design);
            compensationLogService.record("FEATURE_DESIGN", design.getId(), "TIMEOUT_BUILD", true,
                    "构建超时收口", "timeout", TriggerSource.SCHEDULED_COMPENSATION);
        }
    }

    private boolean hasConfirmedSpecForFeature(FeatureDesign design) {
        return specDao.findByProjectId(design.getProjectId()).stream()
                .filter(spec -> spec.getState() == SpecState.CONFIRMED)
                .map(specService::featuresForModule)
                .flatMap(List::stream)
                .anyMatch(feature -> Objects.equals(feature.getFeatureId(), design.getFeatureId()));
    }

    private boolean hasSpecForModule(List<Spec> specs, PlanModule module) {
        return specs.stream().anyMatch(spec ->
                Objects.equals(spec.getModuleId(), module.getModuleId())
                        && Objects.equals(spec.getModuleTitle(), module.getTitle()));
    }

    private int nextSpecVersion(List<Spec> specs) {
        return specs.stream()
                .map(Spec::getVersion)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private String retryHint(String summary) {
        if (summary == null || summary.isBlank()) {
            return "补偿重试";
        }
        return "补偿重试，最近失败原因：" + summary;
    }
}
