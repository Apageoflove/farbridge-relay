// 通过稳定 cursor 和类型条件读取通话镜像。
import { apiFetch } from './http'
import type { CallPage, CallQuery } from '../types/call'

/** 从服务器读取一页通话记录。 */
export async function listCalls(query: CallQuery = {}): Promise<CallPage> {
  const params = new URLSearchParams({ limit: String(query.limit || 30) })
  if (query.cursor) params.set('cursor', query.cursor)
  if (query.type) params.set('call_type', query.type)
  return apiFetch<CallPage>(`/calls?${params.toString()}`)
}

/** 仅删除服务器中的通话镜像。 */
export async function deleteCall(id: string): Promise<void> {
  return apiFetch<void>(`/calls/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
