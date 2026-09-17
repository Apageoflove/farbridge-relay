// 验证 Web Push 只能由明确用户手势开启且订阅提交服务器。
import { describe, expect, it, vi } from 'vitest'
import { enableWebPush } from './useWebPush'

describe('Web Push', () => {
  it('没有用户手势时拒绝请求通知权限', async () => {
    const requestPermission = vi.fn()
    await expect(enableWebPush(false, 'key', vi.fn(), { requestPermission })).rejects.toThrow('用户手势')
    expect(requestPermission).not.toHaveBeenCalled()
  })

  it('用户点击后订阅并提交标准 PushSubscription JSON', async () => {
    const subscription = { toJSON: () => ({ endpoint: 'https://push.example/sub', keys: { p256dh: 'a', auth: 'b' } }) }
    const subscribe = vi.fn().mockResolvedValue(subscription)
    const api = vi.fn().mockResolvedValue({ ok: true })
    await enableWebPush(true, 'AQID', api, {
      requestPermission: vi.fn().mockResolvedValue('granted'),
      getRegistration: vi.fn().mockResolvedValue({ pushManager: { subscribe } }),
    })
    expect(api).toHaveBeenCalledWith('/push/subscriptions', expect.objectContaining({ method: 'POST' }))
  })
})
