package com.changhong.onlinecode.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SavePlatformConfigRequest#workspaceRoot} 的 {@code @Pattern} 校验单测（缺陷修复回归）。
 *
 * <p>验证 WHY：{@code workspaceRoot} 为相对路径（如 {@code project/data}）时，原 DTO 无约束 →
 * 写入接口放行 → 落库 → 运行期 {@code WorkspaceManager.resolve} 才抛
 * {@code IllegalStateException: 不安全的工作区根}（500，原因不直观）。{@code @Pattern} 在 DTO 边界
 * 即拒相对路径 → 400，把"存得进去、用时报错"前移为"写不进去、立即报错"。</p>
 *
 * <p>语义边界：空/空白放行（走 env-fallback，对齐 {@code ConfigService#isNotBlank}）；绝对路径放行；
 * 相对路径拒绝。黑名单根（如 {@code /etc}）属绝对路径，由 {@code WorkspaceManager.isSafeRoot}
 * 在 resolve 期深度校验，本层不重复（避免两套黑名单写法并存）。</p>
 *
 * @author sei-online-code
 */
class SavePlatformConfigRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    /** templateGitlabUrl 无约束；按 propertyPath 过滤 workspaceRoot 违反，求稳健。 */
    private int workspaceRootViolations(String value) {
        SavePlatformConfigRequest req = new SavePlatformConfigRequest();
        req.setWorkspaceRoot(value);
        Set<ConstraintViolation<SavePlatformConfigRequest>> all = validator.validate(req);
        return (int) all.stream()
                .filter(v -> "workspaceRoot".equals(v.getPropertyPath().toString()))
                .count();
    }

    @Test
    void rejectsRelativePath_reportedBug() {
        // 复现 IllegalStateException 的根因输入：相对路径 project/data
        assertEquals(1, workspaceRootViolations("project/data"),
                "相对路径 project/data 必须在 DTO 边界被拒（原 bug：放行后 resolve 期抛 500）");
        assertEquals(1, workspaceRootViolations("relative/dir"),
                "相对路径一律拒绝");
    }

    @Test
    void allowsBlankAndNull_envFallback() {
        // 空/空白/null → 走 env-fallback 默认值，不得被 @Pattern 拦截
        assertEquals(0, workspaceRootViolations(null), "null 放行（@Pattern 默认跳过 null）");
        assertEquals(0, workspaceRootViolations(""), "空串放行（env-fallback）");
        assertEquals(0, workspaceRootViolations("   "), "纯空白放行（对齐 ConfigService.isNotBlank）");
    }

    @Test
    void allowsAbsolutePath() {
        // 绝对路径放行；黑名单根（/etc）的深度校验交由 resolve 期 isSafeRoot，本层不重复
        assertEquals(0, workspaceRootViolations("/tmp/sei-online-code"), "Unix 绝对路径放行");
        assertEquals(0, workspaceRootViolations("/data/workspaces"), "非黑名单绝对路径放行");
        assertEquals(0, workspaceRootViolations("C:\\workspaces\\foo"), "Windows 绝对路径放行");
    }
}
