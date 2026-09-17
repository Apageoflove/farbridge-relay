// 验证短信状态和删除请求的服务器契约。
import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.mock('./http', () => ({ apiFetch: vi.fn() }))
import { apiFetch } from './http'
import { deleteMessage, updateMessageState } from './messages'

describe('message mutations', () => {
  beforeEach(() => vi.mocked(apiFetch).mockReset())
  it('使用 PATCH 更新收藏或置顶', async () => {
    await updateMessageState('m/1', { favorite: true })
    expect(apiFetch).toHaveBeenCalledWith('/messages/m%2F1', { method: 'PATCH', body: JSON.stringify({ favorite: true }) })
  })
  it('删除只针对服务器记录', async () => {
    await deleteMessage('m/1')
    expect(apiFetch).toHaveBeenCalledWith('/messages/m%2F1', { method: 'DELETE' })
  })
})
