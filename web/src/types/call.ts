// 描述只读通话记录及其筛选条件。
import type { CursorPage } from './sms'

export type CallType = 'INCOMING' | 'OUTGOING' | 'MISSED' | 'REJECTED' | 'BLOCKED' | 'VOICEMAIL' | 'UNKNOWN'

export interface CallRecord {
  id: string
  source_id: string
  number: string
  cached_name: string | null
  call_type: CallType
  call_date?: number
  occurred_at?: number
  duration: number
  phone_account_id?: string | null
}

export interface CallQuery {
  cursor?: string
  limit?: number
  type?: CallType | ''
}

export type CallPage = CursorPage<CallRecord>
