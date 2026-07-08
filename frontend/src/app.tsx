import React, { useEffect } from 'react';
import dayjs from 'dayjs';
import { ConfigProvider, theme, App } from '@ead/suid';
import { useUserContext } from '@ead/suid-utils-react';
import { constants } from '@/utils';
import { designTokens } from '@/theme/tokens';

require('dayjs/locale/zh-cn');

const { LANG } = constants;

/**
 * Start the MSW mock worker before the app renders when MOCK is enabled
 * (`pnpm start:mock`). MSW-first (ADR-0002): all Phase 1 data is served by the
 * in-browser worker; there is no real backend this round.
 */
export async function render(oldRender: () => void) {
  if (process.env.MOCK === 'yes') {
    const { startMockWorker } = await import('@/mocks/browser');
    await startMockWorker();
  }
  oldRender();
}

export function rootContainer(container: React.ReactNode) {
  const AppRoot = () => {
    const { currentLocale } = useUserContext();
    const { token } = theme.useToken();
    const microApp = (window as any)?.microApp;
    const globalData = microApp?.getGlobalData() as any;
    const primaryColor = globalData?.config?.themeInfo?.color || token.colorPrimary;

    const locale = LANG[currentLocale];
    useEffect(() => {
      if (currentLocale === 'zh-CN') {
        dayjs.locale('zh-cn');
      } else {
        dayjs.locale('en');
      }
    }, [currentLocale]);

    return (
      <ConfigProvider
        locale={locale}
        theme={{
          ...designTokens,
          token: { ...designTokens.token, colorPrimary: primaryColor },
        }}
      >
        <App>{container}</App>
      </ConfigProvider>
    );
  };

  return <AppRoot />;
}
