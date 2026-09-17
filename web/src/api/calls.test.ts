// 验证通话筛选参数与 FastAPI 契约保持一致。
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('./http', () => ({ apiFetch: vi.fn() }))

import { apiFetch } from './http'
import { deleteCall, listCalls } from './calls'

describe('listCalls', () => {
  beforeEach(() => vi.mocked(apiFetch).mockReset())

  it('uses the server call_type parameter', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ items: [], next_cursor: null })
    await listCalls({ type: 'MISSED', limit: 30 })
    expect(apiFetch).toHaveBeenCalledWith('/calls?limit=30&call_type=MISSED')
  })

  it('删除只针对服务器通话记录', async () => {
    await deleteCall('c/1')
    expect(apiFetch).toHaveBeenCalledWith('/calls/c%2F1', { method: 'DELETE' })
  })
})
