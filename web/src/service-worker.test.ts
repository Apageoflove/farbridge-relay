// 验证 Service Worker 对敏感 API 的 network-only 判定。
import { describe, expect, it } from 'vitest'
import { isSensitiveRequest, normalizePushPayload, pushDestination, staticCacheName } from './service-worker-policy'

describe('service worker policy', () => {
  it('任何 /api 路径都拒绝进入缓存', () => {
    expect(isSensitiveRequest(new URL('https://phone.example.com/api/v1/messages'))).toBe(true)
    expect(isSensitiveRequest(new URL('https://phone.example.com/api/health'))).toBe(true)
  })

  it('静态缓存名称包含明确版本', () => {
    expect(staticCacheName).toBe('phone-mirror-static-v4')
  })

  it('push payload 缺字段或格式异常时使用安全默认值', () => {
    expect(normalizePushPayload(undefined)).toEqual({ title: '远桥', body: '收到新记录', type: 'sms.upserted' })
    expect(normalizePushPayload({ title: 123, body: null, type: [] })).toEqual({ title: '远桥', body: '收到新记录', type: 'sms.upserted' })
  })

  it('短信与通话通知点击后进入对应页面', () => {
    expect(pushDestination('sms.upserted')).toBe('/messages')
    expect(pushDestination('call.upserted')).toBe('/calls')
    expect(pushDestination('unexpected')).toBe('/messages')
  })
})
