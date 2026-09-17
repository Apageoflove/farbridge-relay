// 读取设备健康和仅含公开状态的通知设置。
import { apiFetch } from './http'
import type { DeviceHealth, NotificationSettings } from '../types/device'

/** 获取一加设备当前健康汇总。 */
export function getDevice(): Promise<DeviceHealth> {
  return apiFetch<DeviceHealth>('/device')
}

/** 获取通知开关与 VAPID 公钥，不请求或暴露 Bark key。 */
export function getNotificationSettings(): Promise<NotificationSettings> {
  return apiFetch<NotificationSettings>('/settings/notifications')
}

/** 请求服务器向已配置的 Bark 发送无敏感正文的测试通知。 */
export function sendBarkTest(): Promise<void> {
  return apiFetch('/bark/test', { method: 'POST' })
}

/** 加密保存 Bark 设备码；服务端只返回启用状态，不回传设备码。 */
export function saveBarkSettings(key: string, privacyMode: boolean): Promise<void> {
  return apiFetch('/settings/bark', {
    method: 'PUT',
    body: JSON.stringify({ enabled: true, key, privacy_mode: privacyMode }),
  })
}
