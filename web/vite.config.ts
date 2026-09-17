// 配置 Vue 构建、测试环境与版本化 Service Worker 入口。
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      input: {
        app: 'index.html',
        'service-worker': 'src/service-worker.ts',
      },
      output: {
        entryFileNames: (chunk) => chunk.name === 'service-worker' ? 'service-worker.js' : 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: true,
  },
})
