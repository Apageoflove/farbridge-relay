// 管理 SSE 生命周期、退避重连和页面恢复时的权威刷新。
export interface MirrorChange {
  entity: 'sms' | 'call' | 'device'
  action: 'upserted' | 'deleted' | 'updated'
  sourceId?: string
}

const EVENT_TYPES = ['sms.upserted', 'sms.deleted', 'call.upserted', 'call.deleted', 'device.updated'] as const
const AUTHORITATIVE_REFRESH_MS = 2_000

/** 创建不承载敏感正文的 SSE 控制器。 */
export function createSseController(
  onChange: (change: MirrorChange) => void,
  onResumeRefresh: () => void,
  EventSourceImpl: typeof EventSource = EventSource,
) {
  let source: EventSource | null = null
  let reconnectTimer: number | undefined
  let refreshTimer: number | undefined
  let retryMs = 1_000
  let stopped = false

  /** 清理前台权威刷新定时器，避免重复轮询。 */
  function clearRefreshTimer(): void {
    if (refreshTimer === undefined) return
    window.clearInterval(refreshTimer)
    refreshTimer = undefined
  }

  /** 仅在前台启动一个权威刷新定时器。 */
  function startRefreshTimer(): void {
    if (stopped || document.visibilityState === 'hidden' || refreshTimer !== undefined) return
    refreshTimer = window.setInterval(onResumeRefresh, AUTHORITATIVE_REFRESH_MS)
  }

  /** 解析服务端事件名，只提取变化类型和 source_id。 */
  function receive(type: typeof EVENT_TYPES[number], event: MessageEvent): void {
    let payload: { source_id?: unknown; id?: unknown } = {}
    try { payload = JSON.parse(event.data) as typeof payload } catch { return }
    const [entity, action] = type.split('.') as [MirrorChange['entity'], MirrorChange['action']]
    const candidate = payload.source_id ?? payload.id
    onChange({ entity, action, sourceId: candidate == null ? undefined : String(candidate) })
  }

  /** 建立单个 EventSource 并为断线安排有限退避重连。 */
  function connect(): void {
    if (stopped || source) return
    source = new EventSourceImpl('/api/v1/events/stream', { withCredentials: true })
    EVENT_TYPES.forEach((type) => source?.addEventListener(type, ((event: MessageEvent) => receive(type, event)) as EventListener))
    source.addEventListener('snapshot.applied', (() => onResumeRefresh()) as EventListener)
    source.onopen = () => { retryMs = 1_000 }
    source.onerror = () => {
      source?.close()
      source = null
      if (stopped || reconnectTimer !== undefined) return
      reconnectTimer = window.setTimeout(() => {
        reconnectTimer = undefined
        connect()
      }, retryMs)
      retryMs = Math.min(retryMs * 2, 30_000)
    }
  }

  /** 开始监听变化事件。 */
  function start(): void {
    stopped = false
    connect()
    startRefreshTimer()
  }

  /** 关闭连接与等待中的重连任务。 */
  function stop(): void {
    stopped = true
    source?.close()
    source = null
    if (reconnectTimer !== undefined) window.clearTimeout(reconnectTimer)
    reconnectTimer = undefined
    clearRefreshTimer()
  }

  /** 页面回到前台时重建连接并全量读取服务器当前状态。 */
  function onVisibilityChange(state = document.visibilityState): void {
    if (state !== 'visible') {
      stop()
      return
    }
    stop()
    stopped = false
    onResumeRefresh()
    connect()
    startRefreshTimer()
  }

  return { start, stop, onVisibilityChange }
}
