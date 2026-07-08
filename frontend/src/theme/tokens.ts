/**
 * Runtime design-token layer — single source of truth for the visual system.
 *
 * Applied via ConfigProvider in src/app.tsx. Every `token.*` reference across
 * `@ead/antd-style` createStyles and @ead/suid components inherits from here.
 *
 * Dial reading (tasteskill v2): DESIGN_VARIANCE=2 / MOTION_INTENSITY=1 / VISUAL_DENSITY=8
 *   → Linear/Notion-style dense admin language on top of @ead/suid (Ant v5).
 *
 * Brand fidelity: `colorPrimary` is intentionally NOT set here. It is injected at
 * runtime from the qiankun parent (microApp.config.themeInfo.color) in app.tsx,
 * preserving the existing brand-accent contract. The neutrals / radius / density
 * below are the visual layer that retires the v1 default-Ant identity.
 */
import type { ThemeConfig } from '@ead/suid';
import { theme } from '@ead/suid';

export const designTokens: ThemeConfig = {
  // VD=8: compact tightens paddings/control heights on top of the default
  // light algorithm. MI=1: no motion additions — Ant defaults are already
  // limited to hover/state transitions, which is the ceiling for this dial.
  algorithm: [theme.defaultAlgorithm, theme.compactAlgorithm],
  token: {
    // Cohesive cool-neutral palette. No pure #000 / page-level pure #fff:
    // off-black text, subtle neutral page bg, white containers for depth.
    colorBgLayout: '#f5f6f8',
    colorBgContainer: '#ffffff',
    colorBgElevated: '#ffffff',
    colorBgContainerDisabled: '#f5f6f8',
    colorBorder: '#e4e6ea',
    colorBorderSecondary: '#eef0f3',
    colorText: '#1a1f26',
    colorTextSecondary: '#525a66',
    colorTextTertiary: '#8a9099',
    colorTextQuaternary: '#b0b6bd',
    colorFillTertiary: '#f5f6f8',
    // Shape Consistency Lock: one radius scale (6 base / 8 LG / 4 SM / 2 XS).
    borderRadius: 6,
    borderRadiusLG: 8,
    borderRadiusSM: 4,
    borderRadiusXS: 2,
    // CJK-ready font stack: AlibabaSans (brand) + PingFang/MS YaHei/Noto CJK.
    fontFamily:
      "AlibabaSans, system-ui, -apple-system, 'Segoe UI', Roboto, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Noto Sans CJK SC', sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol'",
    fontFamilyCode:
      "'JetBrains Mono', 'SF Mono', 'Cascadia Code', Menlo, Consolas, 'Courier New', monospace",
    fontSize: 14,
    // MI=1: keep transitions perceptibly snappy, no cinematic easing.
    motionDurationFast: '0.1s',
    motionDurationMid: '0.15s',
    motionDurationSlow: '0.2s',
    // Tinted shadows (Section 4.4): never pure-black drops on light surfaces.
    boxShadow:
      '0 1px 2px rgba(15, 23, 42, 0.04), 0 1px 1px rgba(15, 23, 42, 0.03)',
    boxShadowSecondary: '0 2px 8px rgba(15, 23, 42, 0.06)',
  },
  components: {
    // Business-interface density only. Layout/Menu (AuthLayout shell) are
    // intentionally NOT overridden — the shell stays on Ant defaults per scope.
    Table: {
      cellPaddingBlock: 8,
      headerBg: '#f5f6f8',
      headerColor: '#525a66',
      rowHoverBg: '#f5f6f8',
      borderColor: '#eef0f3',
    },
    Card: {
      paddingLG: 16,
    },
  },
};
