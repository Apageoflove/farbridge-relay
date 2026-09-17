// 启动 Vue PWA、注册认证失效跳转和版本化 Service Worker。
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/http'
import { sessionStore } from './auth/session'
import './styles.css'

setUnauthorizedHandler(() => {
  sessionStore.markUnauthorized()
  if (router.currentRoute.value.path !== '/login') void router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
})

createApp(App).use(router).mount('#app')

export const serviceWorkerUrl = '/service-worker.js?v=4'

/** 在受 TLS 保护的生产环境注册无敏感缓存的 Worker。 */
async function registerServiceWorker(): Promise<void> {
  if (!('serviceWorker' in navigator)) return
  try { await navigator.serviceWorker.register(serviceWorkerUrl, { scope: '/', type: 'module' }) }
  catch (error) { console.warn('Service Worker 注册失败，可继续在线使用', error instanceof Error ? error.message : '') }
}

if (import.meta.env.PROD) void registerServiceWorker()
