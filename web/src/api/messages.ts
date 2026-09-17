// 读取短信镜像并兼容服务器明确公开的字段别名。
import { apiFetch } from './http'
import type { CursorPage, MessageQuery, MessageStatePatch, SmsMessage } from '../types/sms'

/** 将查询条件编码为不会改变 cursor 顺序语义的 URL 参数。 */
function queryString(query: MessageQuery): string {
  const params = new URLSearchParams({ limit: String(query.limit || 30) })
  if (query.cursor) params.set('cursor', query.cursor)
  if (query.sender) params.set('sender', query.sender)
  if (query.q) params.set('q', query.q)
  return params.toString()
}

/** 从服务器读取一页当前短信镜像。 */
export async function listMessages(query: MessageQuery = {}): Promise<CursorPage<SmsMessage>> {
  return apiFetch<CursorPage<SmsMessage>>(`/messages?${queryString(query)}`)
}

/** 保存 PWA 中的收藏或置顶状态，不反向修改安卓短信。 */
export async function updateMessageState(id: string, patch: MessageStatePatch): Promise<SmsMessage> {
  return apiFetch<SmsMessage>(`/messages/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(patch) })
}

/** 仅删除服务器镜像记录，安卓本机数据不受影响。 */
export async function deleteMessage(id: string): Promise<void> {
  return apiFetch<void>(`/messages/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
