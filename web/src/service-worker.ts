// 只缓存版本化静态壳；API 永远网络直连且不写 Cache Storage。
/// <reference lib="webworker" />
import { isCacheableStatic, isSensitiveRequest, normalizePushPayload, pushDestination, staticCacheName } from './service-worker-policy'

declare const self: ServiceWorkerGlobalScope

self.addEventListener('install', () => self.skipWaiting())

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const names = await caches.keys()
    await Promise.all(names.filter((name) => name.startsWith('phone-mirror-static-') && name !== staticCacheName).map((name) => caches.delete(name)))
    await self.clients.claim()
  })())
})

self.addEventListener('push', (event) => {
  event.waitUntil((async () => {
    let input: unknown
    try { input = event.data?.json() }
    catch { input = undefined }
    const payload = normalizePushPayload(input)
    await self.registration.showNotification(payload.title, {
      body: payload.body,
      icon: '/icons/phone-mirror-192.png',
      badge: '/icons/phone-mirror-192.png',
      tag: `yuanqiao-${payload.type}`,
      data: { url: pushDestination(payload.type) },
    })
  })())
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  event.waitUntil((async () => {
    const rawPath = event.notification.data?.url
    const path = typeof rawPath === 'string' ? rawPath : '/messages'
    const destination = new URL(path, self.location.origin).href
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    const existing = windows.find((client) => new URL(client.url).origin === self.location.origin) as WindowClient | undefined
    if (existing) {
      await existing.navigate(destination)
      await existing.focus()
      return
    }
    await self.clients.openWindow(destination)
  })())
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') return
  const url = new URL(request.url)

  if (isSensitiveRequest(url)) {
    event.respondWith(fetch(new Request(request, { cache: 'no-store' })))
    return
  }

  if (isCacheableStatic(url)) {
    event.respondWith((async () => {
      const cache = await caches.open(staticCacheName)
      const cached = await cache.match(request)
      if (cached) return cached
      const response = await fetch(request)
      if (response.ok) await cache.put(request, response.clone())
      return response
    })())
    return
  }

  // 页面导航始终优先网络，离线时只回退到不含业务数据的应用壳。
  if (request.mode === 'navigate') {
    event.respondWith((async () => {
      try { return await fetch(new Request(request, { cache: 'no-store' })) }
      catch { return (await caches.match('/index.html')) || Response.error() }
    })())
  }
})
