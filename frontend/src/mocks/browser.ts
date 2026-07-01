/**
 * MSW browser worker bootstrap. Started from src/app.tsx when MOCK is enabled.
 * The worker script lives at public/mockServiceWorker.js and is served under
 * the app `base` (see public/app.config.json), so serviceWorker.url is
 * base-prefixed to match.
 */
import { setupWorker } from 'msw/browser';
import { base } from '../../public/app.config.json';
import { handlers } from './handlers';

export const worker = setupWorker(...handlers);

export async function startMockWorker(): Promise<void> {
  await worker.start({
    serviceWorker: { url: `${base}/mockServiceWorker.js` },
    onUnhandledRequest: 'bypass',
  });
}
