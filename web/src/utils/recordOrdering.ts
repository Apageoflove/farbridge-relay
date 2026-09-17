// 为短信和通话分页结果提供统一的真实时间倒序与稳定去重。
import type { CallRecord } from '../types/call'
import type { SmsMessage } from '../types/sms'

/** 选取有效的首选时间戳，否则回退到事件发生时间。 */
function eventTime(primary?: number, fallback?: number): number {
  return Number.isFinite(primary) && Number(primary) > 0 ? Number(primary) : Number(fallback) || 0
}

/** 按记录 ID 保留第一次出现的版本，再按时间倒序和 ID 稳定排序。 */
function uniqueLatestFirst<T extends { id: string }>(items: T[], timeOf: (item: T) => number): T[] {
  const byId = new Map<string, T>()
  items.forEach((item) => {
    if (!byId.has(item.id)) byId.set(item.id, item)
  })
  const unique = [...byId.values()]
  return unique.sort((left, right) => timeOf(right) - timeOf(left) || right.id.localeCompare(left.id))
}

/** 短信优先使用 received_at，缺失时使用 occurred_at。 */
export function sortMessagesLatestFirst(items: SmsMessage[]): SmsMessage[] {
  return uniqueLatestFirst(items, (item) => eventTime(item.received_at, item.occurred_at))
    .sort((left, right) => Number(Boolean(right.pinned)) - Number(Boolean(left.pinned))
      || eventTime(right.received_at, right.occurred_at) - eventTime(left.received_at, left.occurred_at)
      || right.id.localeCompare(left.id))
}

/** 通话优先使用 call_date，缺失时使用 occurred_at。 */
export function sortCallsLatestFirst(items: CallRecord[]): CallRecord[] {
  return uniqueLatestFirst(items, (item) => eventTime(item.call_date, item.occurred_at))
}
