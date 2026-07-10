# Frontend Contributor Guide

## Scope
This module contains the React frontend for `sei-online-code`. Work under `frontend/` only and do not depend on backend internals.

## Module Layout
- `src/pages/`: route-level pages such as `Dashboard`, `Login`, and `OnlineCode`
- `src/components/`: reusable UI components
- `src/models/`: global dva models
- `src/services/`: API wrappers
- `src/locales/`: `zh-CN` and `en-US` language packs
- `src/assets/`, `src/theme/`, `src/utils/`: static assets, theme, and utilities
- `config/`: Umi configuration and routing

## Build and Run
Run commands from `frontend/`.

```bash
pnpm start
pnpm build
pnpm lint
pnpm lint-fix
pnpm prettier
```

Use `pnpm`, not `yarn`. The app expects Node.js `>=18`.

## Coding Rules
- Follow the `suid` skill and prefer `@ead/suid` components over raw Ant Design when both fit.
- Keep page code close to its route, and keep shared widgets in `src/components`.
- Use PascalCase for React component directories and files, camelCase for helpers, and `.less` for local styles.
- Respect the existing ESLint, Stylelint, and Prettier setup.
- Keep imports ordered and remove unused imports and variables before submitting.

## UI Conventions
- Prefer `ExtTable`, `ComboList`, `ComboTree`, `MoneyInput`, `Money`, `FilterDate`, `FilterView`, `Scrollbar`, and `Attachment` where applicable.
- Only fall back to raw Ant Design components when the SUID abstraction clearly does not fit the use case.
- Preserve i18n coverage when adding labels or messages; update both locale files when needed.

## Verification
There is no established frontend test suite in this module today. For every UI change, run `pnpm lint`, verify the affected page locally, and check both Chinese and English copy when text changes are involved.

## Commits and PRs
Use `<type>: <description>` commit messages such as `feat: add online code toolbar`. PRs should summarize the affected pages, include screenshots or short recordings for UI changes, and note any backend dependency or route/config update.
