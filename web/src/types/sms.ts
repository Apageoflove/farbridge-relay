// 描述 PWA 内存中的短信镜像和稳定 cursor 分页响应。
export interface SmsMessage {
  id: string
  source_id: string
  sender: string
  body: string
  otp: string | null
  received_at?: number
  occurred_at?: number
  sms_type?: string
  favorite?: boolean
  pinned?: boolean
}

export interface MessageStatePatch {
  favorite?: boolean
  pinned?: boolean
}

export interface CursorPage<T> {
  items: T[]
  next_cursor: string | null
}

export interface MessageQuery {
  cursor?: string
  limit?: number
  sender?: string
  q?: string
}
