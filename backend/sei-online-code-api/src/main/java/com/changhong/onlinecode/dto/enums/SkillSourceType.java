package com.changhong.onlinecode.dto.enums;

/**
 * Skill 导入来源类型。契约 Phase 3 §1.1。
 *
 * <ul>
 *   <li>GITHUB —— source 形如 {@code github:<owner>/<repo>[/path]}</li>
 *   <li>LOCAL  —— source 形如 {@code local:<name>}，内置技能（suid / eadp-backend）为此类</li>
 *   <li>INLINE —— 直接粘贴的 SKILL.md 正文</li>
 * </ul>
 *
 * @author sei-online-code
 */
public enum SkillSourceType {
    GITHUB,
    LOCAL,
    INLINE
}
