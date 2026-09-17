// 定义静态缓存版本和敏感 API 判定，供 Worker 与单元测试共用。
// 品牌与链路名称已更新，提升版本让 iPhone 丢弃旧 manifest 与静态资源。
export const staticCacheName = 'phone-mirror-static-v4'

export interface PushPayload {
  title: string
  body: string
  type: string
}

/** 将不可信 push JSON 收窄为可安全展示的通知内容。 */
export function normalizePushPayload(value: unknown): PushPayload {
  const input = value && typeof value === 'object' ? value as Record<string, unknown> : {}
  return {
    title: typeof input.title === 'string' && input.title.trim() ? input.title : '远桥',
    body: typeof input.body === 'string' && input.body.trim() ? input.body : '收到新记录',
    type: typeof input.type === 'string' && input.type.trim() ? input.type : 'sms.upserted',
  }
}

/** 通知点击只进入对应业务页，未知类型安全回退到短信。 */
export function pushDestination(type: string): '/messages' | '/calls' {
  return type.startsWith('call.') ? '/calls' : '/messages'
}

/** 所有同源 /api 与 /healthz 请求都必须绕过 Cache Storage。 */
export function isSensitiveRequest(url: URL): boolean {
  return url.pathname === '/healthz' || url.pathname === '/api' || url.pathname.startsWith('/api/')
}

/** 仅版本化构建资产与公开壳资源允许静态缓存。 */
export function isCacheableStatic(url: URL): boolean {
  return url.origin === self.location.origin && (
    url.pathname.startsWith('/assets/') ||
    url.pathname.startsWith('/icons/') ||
    url.pathname === '/manifest.webmanifest'
  )
}
