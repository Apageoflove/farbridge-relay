// 描述服务器汇总的安卓设备健康状态和通知配置公开字段。
export type DeviceStatusName = 'ONLINE' | 'DEGRADED' | 'OFFLINE' | 'UNKNOWN'

export interface DeviceHealth {
  status: DeviceStatusName
  status_reason?: string | null
  name: string
  last_seen_at: number | null
  battery_percent: number | null
  charging: boolean | null
  network_type: string | null
  sms_permission_ok: boolean
  call_log_permission_ok: boolean
  pending_event_count: number
  sync_error_count?: number
  sms_last_sync_at?: number | null
  call_last_sync_at?: number | null
  app_version: string
}

export interface NotificationSettings {
  bark_enabled: boolean
  web_push_enabled: boolean
  vapid_public_key: string
}
