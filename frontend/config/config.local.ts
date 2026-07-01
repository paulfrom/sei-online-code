import { defineConfig } from 'umi';

/**
 * 本地联调通过 proxy 转发到内网网关
 */
export default defineConfig({
  define: {
    'process.env.DEV_GATEWAY': '/api-gateway',
  },
});
