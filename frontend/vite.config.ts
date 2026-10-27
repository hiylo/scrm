// Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  define: {
    global: 'globalThis',
  },
  build: {
    outDir: './dist',
    emptyOutDir: true,
    sourcemap: false,
    minify: 'esbuild',
    target: 'es2020',
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return;
          if (/[\\/]node_modules[\\/](react|react-dom|react-router-dom)[\\/]/.test(id)) {
            return 'vendor-react';
          }
          if (/[\\/]node_modules[\\/](antd|@ant-design)[\\/]/.test(id)) {
            return 'vendor-antd';
          }
          if (/[\\/]node_modules[\\/]echarts[\\/]/.test(id)) {
            return 'vendor-echarts';
          }
        },
      },
    },
  },
  esbuild: {
    drop: mode === 'production' ? ['console', 'debugger'] : [],
  },
  base: '/',
  server: {
    port: 3002,
    host: true,
    // 前后端分离部署：开发期由 Vite 反向代理到后端，避免同源 404 与 CORS 配置漂移。
    // 生产期由后端静态托管 dist 或 Nginx 反代，走同源即可。
    proxy: {
      '/scrm': { target: 'http://127.0.0.1:8840', changeOrigin: true },
      '/api': { target: 'http://127.0.0.1:8840', changeOrigin: true },
    },
  },
}));
