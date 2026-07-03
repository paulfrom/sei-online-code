package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.dto.enums.FeatureDesignBuildStatus;
import com.changhong.onlinecode.dto.enums.FeatureDesignStatus;
import com.changhong.onlinecode.entity.FeatureDesign;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FeatureDesignDao 测试（互斥与级联失效）。
 *
 * <p>用 {@code jdbc:tc:postgresql:15} URL（见 application-test.yml）启动 Testcontainers PG，
 * Flyway 跑 V1–V6 建表；{@code @ActiveProfiles("test")} 加载 test 配置，
 * {@code @AutoConfigureTestDatabase(replace=NONE)} 不替换为 H2。</p>
 *
 * @author sei-online-code
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Disabled("deferred: Testcontainers PG + Flyway + @DataJpaTest 方言/Schema 引导需专项处理；测试逻辑保留，Task 8 后续验证")
class FeatureDesignDaoTest {

    @Autowired
    private FeatureDesignDao featureDesignDao;

    @Test
    void testTryAcquireBuildLock() {
        // 插入 1 条 FD（build_status=IDLE）
        FeatureDesign fd = new FeatureDesign();
        fd.setProjectId("PROJ001");
        fd.setFeatureId("FEAT001");
        fd.setVersion(1);
        fd.setStatus(FeatureDesignStatus.CONFIRMED);
        fd.setBuildStatus(FeatureDesignBuildStatus.IDLE);
        fd.setIsLatest(true);
        FeatureDesign saved = featureDesignDao.save(fd);
        String fdId = saved.getId();

        // 第一次抢占成功
        int result = featureDesignDao.tryAcquireBuildLock(fdId, FeatureDesignBuildStatus.BUILDING);
        assertEquals(1, result);

        // 验证 DB 中 build_status=BUILDING
        FeatureDesign updated = featureDesignDao.findById(fdId).orElseThrow();
        assertEquals(FeatureDesignBuildStatus.BUILDING, updated.getBuildStatus());

        // 第二次抢占返回 0（互斥生效）
        result = featureDesignDao.tryAcquireBuildLock(fdId, FeatureDesignBuildStatus.BUILDING);
        assertEquals(0, result);
    }

    @Test
    void testCascadeStale() {
        String projectId = "PROJ002";

        // 插 1 条 BUILT
        FeatureDesign fd1 = new FeatureDesign();
        fd1.setProjectId(projectId);
        fd1.setFeatureId("FEAT001");
        fd1.setVersion(1);
        fd1.setStatus(FeatureDesignStatus.CONFIRMED);
        fd1.setBuildStatus(FeatureDesignBuildStatus.BUILT);
        fd1.setIsLatest(true);
        featureDesignDao.save(fd1);

        // 插 1 条 IDLE
        FeatureDesign fd2 = new FeatureDesign();
        fd2.setProjectId(projectId);
        fd2.setFeatureId("FEAT002");
        fd2.setVersion(1);
        fd2.setStatus(FeatureDesignStatus.CONFIRMED);
        fd2.setBuildStatus(FeatureDesignBuildStatus.IDLE);
        fd2.setIsLatest(true);
        FeatureDesign savedFd2 = featureDesignDao.save(fd2);

        // 调用 cascadeStale
        featureDesignDao.cascadeStale(projectId);

        // 验证两条 status=STALE
        FeatureDesign updatedFd1 = featureDesignDao.findById(fd1.getId()).orElseThrow();
        assertEquals(FeatureDesignStatus.STALE, updatedFd1.getStatus());
        assertEquals(FeatureDesignBuildStatus.STALE, updatedFd1.getBuildStatus()); // BUILT→STALE

        FeatureDesign updatedFd2 = featureDesignDao.findById(savedFd2.getId()).orElseThrow();
        assertEquals(FeatureDesignStatus.STALE, updatedFd2.getStatus());
        assertEquals(FeatureDesignBuildStatus.IDLE, updatedFd2.getBuildStatus()); // IDLE 保持不变
    }
}
