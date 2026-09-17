// 封装标准 Web Push 订阅，并强制通知授权来自用户点击。
type ApiCaller = <T>(path: string, options?: RequestInit) => Promise<T>
interface PushDependencies {
  requestPermission?: () => Promise<NotificationPermission>
  getRegistration?: () => Promise<ServiceWorkerRegistration>
}

/** 将 URL-safe Base64 VAPID 公钥转换为 Push API 所需字节。 */
export function urlBase64ToUint8Array(value: string): Uint8Array<ArrayBuffer> {
  const padded = `${value}${'='.repeat((4 - value.length % 4) % 4)}`
  const binary = window.atob(padded.replace(/-/g, '+').replace(/_/g, '/'))
  return Uint8Array.from(binary, (character) => character.charCodeAt(0))
}

/** 经用户手势请求权限、建立订阅并把标准 JSON 交给服务器保存。 */
export async function enableWebPush(
  userGesture: boolean,
  vapidPublicKey: string,
  api: ApiCaller,
  dependencies: PushDependencies = {},
): Promise<void> {
  if (!userGesture) throw new Error('开启通知必须由用户手势触发')
  if (!dependencies.getRegistration && !('serviceWorker' in navigator)) throw new Error('当前浏览器不支持 Service Worker')
  if (!dependencies.getRegistration && !('PushManager' in window)) throw new Error('请在 iPhone 主屏幕版远桥中开启通知')
  const requestPermission = dependencies.requestPermission || (() => Notification.requestPermission())
  if (await requestPermission() !== 'granted') throw new Error('通知权限未授予，请在系统设置中允许通知')
  const registration = dependencies.getRegistration ? await dependencies.getRegistration() : await navigator.serviceWorker.ready
  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(vapidPublicKey),
  })
  await api('/push/subscriptions', { method: 'POST', body: JSON.stringify(subscription.toJSON()) })
}
