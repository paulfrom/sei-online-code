package com.changhong.onlinecode.service.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 技能内容锁的规范 hash 计算器。契约 Phase 3 §6（normative，跨端一致）。
 *
 * <pre>
 * h = sha256()
 * writePart(h, "v1")
 * writePart(h, source)
 * writePart(h, name)
 * writePart(h, description)
 * writePart(h, content)
 * computedHash = "sha256:" + hex(h.digest())
 *
 * writePart(h, s):                    // length-prefixed，消除边界歧义
 *     h.update(utf8(len(utf8(s))))    // 十进制 ascii 长度
 *     h.update(0x00)                  // 分隔符
 *     h.update(utf8(s))
 * </pre>
 *
 * <p>服务端权威计算，前端不重算。null 部分按空串处理（长度 0）。</p>
 *
 * @author sei-online-code
 */
public final class SkillHasher {

    private SkillHasher() {
    }

    /**
     * 按 §6 recipe 计算技能内容锁。
     *
     * @param source      导入来源
     * @param name        技能名
     * @param description 技能描述
     * @param content     SKILL.md 正文
     * @return 形如 {@code sha256:<hex>} 的内容锁
     */
    public static String compute(String source, String name, String description, String content) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，不可达
            throw new IllegalStateException("SHA-256 不可用", e);
        }
        writePart(digest, "v1");
        writePart(digest, source);
        writePart(digest, name);
        writePart(digest, description);
        writePart(digest, content);
        return "sha256:" + toHex(digest.digest());
    }

    /**
     * length-prefixed 写入一个部分：十进制 ascii 长度 + 0x00 分隔符 + utf8 正文。
     *
     * @param digest 摘要
     * @param s      部分（null 视为空串）
     */
    private static void writePart(MessageDigest digest, String s) {
        byte[] bytes = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0x00);
        digest.update(bytes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
