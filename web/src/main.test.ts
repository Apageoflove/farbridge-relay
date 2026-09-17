// 验证生产 PWA 使用显式版本化 URL 注册 Service Worker。
import { describe, expect, it } from 'vitest'

describe('service worker registration', () => {
  it('使用 v4 URL 强制 iOS 获取新版 Worker', async () => {
    // main.ts 会挂载真实应用；测试先提供宿主节点，避免无关 Vue 警告掩盖结果。
    document.body.innerHTML = '<div id="app"></div>'
    const { serviceWorkerUrl } = await import('./main')
    expect(serviceWorkerUrl).toBe('/service-worker.js?v=4')
  })
})
