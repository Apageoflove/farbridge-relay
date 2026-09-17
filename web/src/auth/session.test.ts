// 验证登录、注销与会话恢复不使用浏览器持久存储。
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createSessionStore } from './session'

describe('session store', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', { setItem: vi.fn(), getItem: vi.fn(), removeItem: vi.fn() })
  })

  it('登录后只在内存保留认证状态和 CSRF', async () => {
    const api = vi.fn().mockResolvedValue({ authenticated: true, csrf_token: 'token' })
    const store = createSessionStore(api)
    await store.login('admin', 'pass')
    expect(store.authenticated.value).toBe(true)
    expect(localStorage.setItem).not.toHaveBeenCalled()
  })

  it('注销调用服务器并清空状态', async () => {
    const api = vi.fn().mockResolvedValue({ authenticated: true, csrf_token: 'token' })
    const store = createSessionStore(api)
    await store.login('admin', 'pass')
    await store.logout()
    expect(api).toHaveBeenLastCalledWith('/auth/logout', { method: 'POST' })
    expect(store.authenticated.value).toBe(false)
  })
})
