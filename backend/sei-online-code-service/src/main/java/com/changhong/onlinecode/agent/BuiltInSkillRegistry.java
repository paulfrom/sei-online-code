package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.service.support.SkillHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 内置技能注册表（multica 维度 g）。契约 Phase 3 §4（资源化模型）。
 *
 * <p>内置技能（suid/eadp-backend/project-planning/feature-design）不再以 {@code oc_skill} 行存在，
 * 而是 vendor 到 classpath {@code skills/<name>/SKILL.md}（+ {@code references/**} 辅助文件）。agent 经
 * {@code oc_agent_skill} 以 synthetic id {@code builtin:<name>} 绑定（join 表 {@code skill_id} 不加 FK，
 * 见 V7/V11）。本注册表把 {@code builtin:<name>} 解析为可 materialize 的 {@link SkillMaterializer.SkillPayload}：
 * 从 classpath 加载 SKILL.md 与辅助文件，按 §6 recipe（origin = {@code builtin:<name>}）算内容锁。</p>
 *
 * <p>仅处理 {@code builtin:} 前缀；其余 id 返回 {@link Optional#empty()}，由 caller 走 DB 路径。
 * {@code name} 须匹配 {@code ^[a-z0-9][a-z0-9-]{0,63}$}（防 classpath 路径注入；name 来源为 V11 seed /
 * 前端常量，本受信，正则作 defense-in-depth）。内容不可变（classpath）→ hash 仅作 {@code .lock} 幂等标记。</p>
 *
 * @author sei-online-code
 */
@Component
public class BuiltInSkillRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuiltInSkillRegistry.class);

    /** synthetic id 前缀，对齐 V11 join 表 {@code skill_id} 值。 */
    public static final String PREFIX = "builtin:";

    /** 内置技能名规则（与 SkillDto.name 一致，materialize 为目录名）。 */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    private static final String RESOURCE_BASE = "skills/";

    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * 把 {@code builtin:<name>} 解析为 {@link SkillMaterializer.SkillPayload}。
     *
     * @param skillId 技能 id（仅处理 {@code builtin:} 前缀）
     * @return 已加载的 payload；非 {@code builtin:} / name 非法 / 资源缺失 → empty
     */
    public Optional<SkillMaterializer.SkillPayload> resolve(String skillId) {
        if (skillId == null || !skillId.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String name = skillId.substring(PREFIX.length());
        if (!NAME_PATTERN.matcher(name).matches()) {
            LOGGER.warn("resolve: 非法内置技能名，跳过 skillId={}", skillId);
            return Optional.empty();
        }

        Resource skillMd = new ClassPathResource(RESOURCE_BASE + name + "/SKILL.md");
        if (!skillMd.exists()) {
            LOGGER.warn("resolve: 内置技能 SKILL.md 不存在 name={}", name);
            return Optional.empty();
        }
        try {
            String content = readString(skillMd);
            List<SkillMaterializer.SkillFileRef> files = loadAuxFiles(name);
            String hash = SkillHasher.compute(PREFIX + name, name, null, content);
            return Optional.of(new SkillMaterializer.SkillPayload(name, content, hash, files));
        } catch (IOException e) {
            LOGGER.warn("resolve: 加载内置技能失败 name={}", name, e);
            return Optional.empty();
        }
    }

    /**
     * 加载 {@code skills/<name>/references/**} 为辅助文件列表（path 相对技能目录，如 {@code references/foo.md}）。
     *
     * @param name 技能名
     * @return 辅助文件列表（无则空）
     * @throws IOException 资源列举或读取失败
     */
    private List<SkillMaterializer.SkillFileRef> loadAuxFiles(String name) throws IOException {
        String base = RESOURCE_BASE + name + "/";
        Resource[] refs;
        try {
            refs = resolver.getResources("classpath:" + base + "references/**");
        } catch (IOException e) {
            // references/ 目录不存在 → 无辅助文件（stub 技能的正常情况），非失败
            return List.of();
        }
        List<SkillMaterializer.SkillFileRef> files = new ArrayList<>();
        for (Resource ref : refs) {
            if (!ref.exists() || !ref.isReadable()) {
                continue;
            }
            String url = ref.getURL().toString();
            int idx = url.indexOf(base);
            if (idx < 0) {
                continue;
            }
            String rel = url.substring(idx + base.length());
            // 跳过目录条目（相对路径为空或以 / 结尾）
            if (rel.isEmpty() || rel.endsWith("/")) {
                continue;
            }
            files.add(new SkillMaterializer.SkillFileRef(rel, readString(ref)));
        }
        return files;
    }

    private static String readString(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
