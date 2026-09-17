// 验证同源 Cookie、CSRF、超时和未认证跳转边界。
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, ApiError, setCsrfToken } from './http'

describe('apiFetch', () => {
  beforeEach(() => setCsrfToken(null))

  it('状态改变请求携带 Cookie 与 CSRF header', async () => {
    setCsrfToken('csrf-test')
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), {
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/auth/logout', { method: 'POST' })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/logout', expect.objectContaining({
      credentials: 'include',
      headers: expect.objectContaining({ 'X-CSRF-Token': 'csrf-test' }),
    }))
  })

  it('请求超时后抛出可识别错误', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', vi.fn((_url, init: RequestInit) => new Promise((_resolve, reject) => {
      init.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
    })))

    const pending = expect(apiFetch('/messages', {}, 50)).rejects.toMatchObject({ code: 'TIMEOUT' })
    await vi.advanceTimersByTimeAsync(60)
    await pending
    vi.useRealTimers()
  })

  it('401 通知认证层跳转登录页', async () => {
    const unauthorized = vi.fn()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })))

    await expect(apiFetch('/messages', {}, 1000, unauthorized)).rejects.toBeInstanceOf(ApiError)
    expect(unauthorized).toHaveBeenCalledOnce()
  })
})
