// 验证 SSE 仅用变化类型和 ID 驱动刷新、删除、重连与前台恢复。
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createSseController } from './useSse'

class FakeEventSource {
  static instances: FakeEventSource[] = []
  listeners = new Map<string, EventListener>()
  onerror: (() => void) | null = null
  close = vi.fn()
  constructor(public url: string) { FakeEventSource.instances.push(this) }
  addEventListener(type: string, listener: EventListener) { this.listeners.set(type, listener) }
  emit(type: string, payload: object) { this.listeners.get(type)?.({ data: JSON.stringify(payload) } as MessageEvent) }
}

describe('SSE controller', () => {
  afterEach(() => vi.useRealTimers())

  it('删除事件只交付类型和 source id', () => {
    FakeEventSource.instances = []
    const onChange = vi.fn()
    const controller = createSseController(onChange, () => {}, FakeEventSource as unknown as typeof EventSource)
    controller.start()
    FakeEventSource.instances[0].emit('sms.deleted', { source_id: '42' })
    expect(onChange).toHaveBeenCalledWith({ entity: 'sms', action: 'deleted', sourceId: '42' })
  })

  it('监听短信、通话、设备和快照完成事件', () => {
    FakeEventSource.instances = []
    const onChange = vi.fn()
    const refresh = vi.fn()
    const controller = createSseController(onChange, refresh, FakeEventSource as unknown as typeof EventSource)
    controller.start()
    const source = FakeEventSource.instances[0]
    source.emit('sms.upserted', { source_id: 's1' })
    source.emit('call.upserted', { source_id: 'c1' })
    source.emit('device.updated', { id: 'android-device-01' })
    source.emit('snapshot.applied', {})

    expect(onChange.mock.calls.map(([change]) => change.entity)).toEqual(['sms', 'call', 'device'])
    expect(refresh).toHaveBeenCalledOnce()
    controller.stop()
  })

  it('连接失败后退避重连', () => {
    vi.useFakeTimers()
    FakeEventSource.instances = []
    const controller = createSseController(() => {}, () => {}, FakeEventSource as unknown as typeof EventSource)
    controller.start()
    FakeEventSource.instances[0].onerror?.()
    vi.advanceTimersByTime(1000)
    expect(FakeEventSource.instances).toHaveLength(2)
    vi.useRealTimers()
  })

  it('同一连接重复报错只安排一个重连任务', () => {
    vi.useFakeTimers()
    FakeEventSource.instances = []
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout')
    const controller = createSseController(() => {}, () => {}, FakeEventSource as unknown as typeof EventSource)
    controller.start()
    FakeEventSource.instances[0].onerror?.()
    FakeEventSource.instances[0].onerror?.()
    expect(setTimeoutSpy).toHaveBeenCalledTimes(1)
    controller.stop()
  })

  it('页面恢复前台时触发权威全量刷新', () => {
    FakeEventSource.instances = []
    const refresh = vi.fn()
    const controller = createSseController(() => {}, refresh, FakeEventSource as unknown as typeof EventSource)
    controller.onVisibilityChange('visible')
    expect(refresh).toHaveBeenCalledOnce()
    expect(FakeEventSource.instances).toHaveLength(1)
    controller.stop()
  })

  it('前台每2秒权威刷新且重复 start 不创建多个定时器', () => {
    vi.useFakeTimers()
    const refresh = vi.fn()
    const controller = createSseController(() => {}, refresh, FakeEventSource as unknown as typeof EventSource)
    controller.start()
    controller.start()
    vi.advanceTimersByTime(2_000)
    expect(refresh).toHaveBeenCalledOnce()
    vi.advanceTimersByTime(2_000)
    expect(refresh).toHaveBeenCalledTimes(2)
    controller.stop()
    vi.advanceTimersByTime(10_000)
    expect(refresh).toHaveBeenCalledTimes(2)
  })

  it('页面隐藏停止轮询，恢复前台立即刷新并恢复轮询', () => {
    vi.useFakeTimers()
    const refresh = vi.fn()
    const controller = createSseController(() => {}, refresh, FakeEventSource as unknown as typeof EventSource)
    controller.start()
    controller.onVisibilityChange('hidden')
    vi.advanceTimersByTime(30_000)
    expect(refresh).not.toHaveBeenCalled()
    controller.onVisibilityChange('visible')
    expect(refresh).toHaveBeenCalledOnce()
    vi.advanceTimersByTime(2_000)
    expect(refresh).toHaveBeenCalledTimes(2)
    controller.stop()
  })

  it('snapshot applied 直接触发权威刷新而不伪装成单条变化', () => {
    FakeEventSource.instances = []
    const onChange = vi.fn()
    const refresh = vi.fn()
    const controller = createSseController(onChange, refresh, FakeEventSource as unknown as typeof EventSource)
    controller.start()
    FakeEventSource.instances[0].emit('snapshot.applied', {})
    expect(refresh).toHaveBeenCalledOnce()
    expect(onChange).not.toHaveBeenCalled()
    controller.stop()
  })
})
