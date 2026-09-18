// 验证主要页面的加载、空态、错误恢复、分页、筛选、复制与状态提示。
import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MessagesView from './MessagesView.vue'
import CallsView from './CallsView.vue'
import DeviceView from './DeviceView.vue'
import SettingsView from './SettingsView.vue'

beforeEach(() => localStorage.clear())

class ViewEventSource {
  static instance: ViewEventSource
  listeners = new Map<string, EventListener>()
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  constructor() { ViewEventSource.instance = this }
  addEventListener(type: string, listener: EventListener) { this.listeners.set(type, listener) }
  close() {}
  emit(type: string, data: object) { this.listeners.get(type)?.({ data: JSON.stringify(data) } as MessageEvent) }
}

describe('MessagesView', () => {
  it('权威刷新以服务器首页替换列表并移除已过期项', async () => {
    const original = globalThis.EventSource
    Object.defineProperty(globalThis, 'EventSource', { configurable: true, value: ViewEventSource })
    const load = vi.fn()
      .mockResolvedValueOnce({ items: [
        { id: 'keep', source_id: '1', sender: '保留', body: '新', otp: null, received_at: 200 },
        { id: 'expired', source_id: '2', sender: '过期', body: '旧', otp: null, received_at: 100 },
      ], next_cursor: null })
      .mockResolvedValueOnce({ items: [{ id: 'keep', source_id: '1', sender: '保留', body: '新', otp: null, received_at: 200 }], next_cursor: null })
    const { unmount } = render(MessagesView, { props: { load } })
    try {
      expect(await screen.findByText('过期')).toBeInTheDocument()
      ViewEventSource.instance.emit('snapshot.applied', {})
      await waitFor(() => expect(screen.queryByText('过期')).not.toBeInTheDocument())
      expect(screen.getByText('保留')).toBeInTheDocument()
    } finally {
      unmount()
      Object.defineProperty(globalThis, 'EventSource', { configurable: true, value: original })
    }
  })

  it('sms.deleted 事件立即回源刷新，服务器保留的收藏短信不闪退', async () => {
    const original = globalThis.EventSource
    Object.defineProperty(globalThis, 'EventSource', { configurable: true, value: ViewEventSource })
    const favorite = { id: 'fav', source_id: 'source-fav', sender: '收藏发件人', body: '永久保留', otp: null, received_at: 200, favorite: true }
    const load = vi.fn().mockResolvedValue({ items: [favorite], next_cursor: null })
    const { unmount } = render(MessagesView, { props: { load } })
    try {
      expect(await screen.findByText('收藏发件人')).toBeInTheDocument()
      ViewEventSource.instance.emit('sms.deleted', { source_id: 'source-fav' })
      await waitFor(() => expect(load).toHaveBeenCalledTimes(2))
      expect(screen.getByText('收藏发件人')).toBeInTheDocument()
    } finally {
      unmount()
      Object.defineProperty(globalThis, 'EventSource', { configurable: true, value: original })
    }
  })

  it('置顶消息优先，组内仍按最新时间排列', async () => {
    const load = vi.fn().mockResolvedValue({ items: [
      { id: 'new', source_id: '1', sender: '新消息', body: '新', otp: null, received_at: 300, pinned: false, favorite: false },
      { id: 'pin-old', source_id: '2', sender: '旧置顶', body: '旧', otp: null, received_at: 100, pinned: true, favorite: false },
      { id: 'pin-new', source_id: '3', sender: '新置顶', body: '新置顶', otp: null, received_at: 200, pinned: true, favorite: false },
    ], next_cursor: null })
    const { container } = render(MessagesView, { props: { load } })
    await screen.findByText('新消息')
    expect([...container.querySelectorAll('.sms-card')].map((node) => node.textContent)).toEqual([
      expect.stringContaining('新置顶'), expect.stringContaining('旧置顶'), expect.stringContaining('新消息'),
    ])
  })

  it('收藏与置顶乐观更新，失败时回滚', async () => {
    const update = vi.fn().mockRejectedValue(new Error('保存失败'))
    const load = vi.fn().mockResolvedValue({ items: [{ id: 'm1', source_id: '1', sender: '10086', body: '内容', otp: null, received_at: 100, pinned: false, favorite: false }], next_cursor: null })
    render(MessagesView, { props: { load, update } })
    await screen.findByText('10086')
    await fireEvent.click(screen.getByRole('button', { name: '管理短信' }))
    await fireEvent.click(screen.getByRole('button', { name: '收藏短信' }))
    expect(update).toHaveBeenCalledWith('m1', { favorite: true })
    expect(await screen.findByText('保存失败')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '收藏短信', hidden: true })).toHaveAttribute('aria-pressed', 'false')
  })

  it('删除使用应用内二次确认且仅移除服务器列表记录', async () => {
    const remove = vi.fn().mockResolvedValue(undefined)
    const load = vi.fn().mockResolvedValue({ items: [{ id: 'm1', source_id: '1', sender: '10086', body: '待删除', otp: null, received_at: 100 }], next_cursor: null })
    render(MessagesView, { props: { load, remove } })
    await screen.findByText('待删除')
    await fireEvent.click(screen.getByRole('button', { name: '管理短信' }))
    await fireEvent.click(screen.getByRole('button', { name: '删除短信' }))
    expect(screen.getByRole('dialog', { name: '确认删除短信' })).toBeInTheDocument()
    expect(remove).not.toHaveBeenCalled()
    await fireEvent.click(screen.getByRole('button', { name: '确认仅删除服务器记录' }))
    expect(remove).toHaveBeenCalledWith('m1')
    await waitFor(() => expect(screen.queryByText('待删除')).not.toBeInTheDocument())
  })
  it('按真实时间倒序展示常规短信摘要并保留可复制验证码', async () => {
    const load = vi.fn().mockResolvedValue({
      items: [
        { id: 'old', source_id: '1', sender: '10010', body: '较早的短信', otp: null, received_at: 100 },
        { id: 'new', source_id: '2', sender: '95588', body: '您的验证码是 654321，请勿泄露。', otp: '654321', occurred_at: 300 },
      ],
      next_cursor: null,
    })
    const { container } = render(MessagesView, { props: { load } })

    expect(await screen.findByText('95588')).toBeInTheDocument()
    const cards = [...container.querySelectorAll('.sms-card')]
    expect(cards).toHaveLength(2)
    expect(cards[0]).toHaveTextContent('95588')
    expect(cards[0]).toHaveTextContent('您的验证码是 654321，请勿泄露。')
    expect(cards[0].querySelector('.contact-avatar')).toHaveTextContent('9')
    await fireEvent.click(cards[0])
    expect(screen.getByRole('button', { name: '复制验证码 654321' })).toBeInTheDocument()
  })

  it('权威刷新更新同一短信记录而不是保留旧卡片内容', async () => {
    const load = vi.fn()
      .mockResolvedValueOnce({ items: [{ id: 'same', source_id: '1', sender: '10086', body: '旧内容', otp: null, received_at: 100 }], next_cursor: null })
      .mockResolvedValueOnce({ items: [{ id: 'same', source_id: '1', sender: '10086', body: '新内容', otp: null, received_at: 200 }], next_cursor: null })
    render(MessagesView, { props: { load } })
    expect(await screen.findByText('旧内容')).toBeInTheDocument()
    await fireEvent.click(screen.getByRole('button', { name: '刷新消息' }))
    expect(await screen.findByText('新内容')).toBeInTheDocument()
    expect(screen.queryByText('旧内容')).not.toBeInTheDocument()
  })

  it('手动刷新期间禁用按钮并在完成后显示结果，同时合并新消息置顶', async () => {
    let finishRefresh!: (page: { items: Array<Record<string, unknown>>; next_cursor: null }) => void
    const pendingRefresh = new Promise<{ items: Array<Record<string, unknown>>; next_cursor: null }>((resolve) => { finishRefresh = resolve })
    const load = vi.fn()
      .mockResolvedValueOnce({ items: [{ id: 'old', source_id: '1', sender: '10086', body: '原有消息', otp: null, received_at: 100 }], next_cursor: null })
      .mockReturnValueOnce(pendingRefresh)
    const { container } = render(MessagesView, { props: { load } })
    await screen.findByText('原有消息')

    const refreshButton = screen.getByRole('button', { name: '刷新消息' })
    await fireEvent.click(refreshButton)
    expect(refreshButton).toBeDisabled()
    expect(screen.getByText('正在刷新消息…')).toBeVisible()
    expect(load).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: undefined, limit: 30 }))

    finishRefresh({ items: [{ id: 'new', source_id: '2', sender: '95588', body: '新验证码 654321', otp: '654321', received_at: 300 }], next_cursor: null })
    expect(await screen.findByText('已刷新，共收到 2 条消息')).toBeVisible()
    const cards = [...container.querySelectorAll('.sms-card')]
    expect(cards).toHaveLength(2)
    expect(cards[0]).toHaveTextContent('95588')
    expect(cards[1]).toHaveTextContent('10086')
    expect(refreshButton).not.toBeDisabled()
  })

  it('已有短信时后台刷新不闪成加载态且失败不覆盖列表', async () => {
    let rejectRefresh!: (reason: Error) => void
    const pendingRefresh = new Promise<never>((_, reject) => { rejectRefresh = reject })
    const load = vi.fn()
      .mockResolvedValueOnce({ items: [{ id: 'keep', source_id: '1', sender: '10086', body: '保留内容', otp: null, received_at: 100 }], next_cursor: null })
      .mockReturnValueOnce(pendingRefresh)
    render(MessagesView, { props: { load } })
    expect(await screen.findByText('保留内容')).toBeInTheDocument()

    await fireEvent.click(screen.getByRole('button', { name: '刷新消息' }))
    expect(screen.getByText('保留内容')).toBeInTheDocument()
    expect(screen.queryByText('正在读取服务器镜像…')).not.toBeInTheDocument()
    rejectRefresh(new Error('网络抖动'))
    expect(await screen.findByText('网络抖动')).toBeInTheDocument()
    expect(screen.getByText('保留内容')).toBeInTheDocument()
    expect(screen.queryByText('无法读取消息')).not.toBeInTheDocument()
  })

  it('空列表说明下一步并可刷新', async () => {
    const load = vi.fn().mockResolvedValue({ items: [], next_cursor: null })
    render(MessagesView, { props: { load } })
    expect(screen.getByText('正在读取服务器镜像…')).toBeInTheDocument()
    expect(await screen.findByText('还没有镜像消息')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '重新读取' })).toBeInTheDocument()
  })

  it('搜索和发送方筛选作为稳定查询参数发送', async () => {
    const load = vi.fn().mockResolvedValue({ items: [], next_cursor: null })
    render(MessagesView, { props: { load } })
    await screen.findByText('还没有镜像消息')
    await fireEvent.update(screen.getByLabelText('搜索消息'), '验证码')
    await fireEvent.update(screen.getByLabelText('发送方'), '10086')
    await fireEvent.click(screen.getByRole('button', { name: '应用筛选' }))
    expect(load).toHaveBeenLastCalledWith(expect.objectContaining({ q: '验证码', sender: '10086', cursor: undefined }))
  })

  it('按 cursor 追加下一页且复制验证码后播报', async () => {
    const load = vi.fn()
      .mockResolvedValueOnce({ items: [{ id: 'm1', source_id: '1', sender: '10086', body: '验证码 123456', otp: '123456', received_at: 1700000000 }], next_cursor: 'next' })
      .mockResolvedValueOnce({ items: [
        { id: 'm1', source_id: '1', sender: '10086', body: '验证码 123456', otp: '123456', received_at: 1700000000 },
        { id: 'm2', source_id: '2', sender: '95588', body: '较早消息', otp: null, received_at: 1600000000 },
      ], next_cursor: null })
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    render(MessagesView, { props: { load } })
    await fireEvent.click(await screen.findByRole('button', { name: '展开短信全文' }))
    await fireEvent.click(await screen.findByRole('button', { name: '复制验证码 123456' }))
    expect(await screen.findByText('验证码已复制')).toBeInTheDocument()
    await fireEvent.click(screen.getByRole('button', { name: '加载更多' }))
    expect(load).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: 'next' }))
    await waitFor(() => expect(document.querySelectorAll('.sms-card')).toHaveLength(2))
  })

  it('请求失败展示原因和重试入口', async () => {
    render(MessagesView, { props: { load: vi.fn().mockRejectedValue(new Error('网络不可用')) } })
    expect(await screen.findByText('无法读取消息')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument()
  })
})

describe('CallsView', () => {
  it('复制真实电话号码并播报成功或失败', async () => {
    const load = vi.fn().mockResolvedValue({ items: [{ id: 'c1', source_id: '1', number: '13800138000', cached_name: '家里', call_type: 'INCOMING', call_date: 100, duration: 5 }], next_cursor: null })
    const writeText = vi.fn().mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('拒绝访问'))
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    render(CallsView, { props: { load } })
    const copy = await screen.findByRole('button', { name: '复制电话号码 13800138000' })
    await fireEvent.click(copy)
    expect(writeText).toHaveBeenCalledWith('13800138000')
    expect(await screen.findByText('电话号码已复制')).toBeVisible()
    await fireEvent.click(copy)
    expect(await screen.findByText('复制电话号码失败')).toBeVisible()
  })

  it('显示30天保留规则并可二次确认删除服务器通话记录', async () => {
    const remove = vi.fn().mockResolvedValue(undefined)
    const load = vi.fn().mockResolvedValue({ items: [{ id: 'c1', source_id: '1', number: '10010', cached_name: null, call_type: 'INCOMING', call_date: 100, duration: 5 }], next_cursor: null })
    render(CallsView, { props: { load, remove } })
    expect(await screen.findByText('服务器最多保留30天')).toBeInTheDocument()
    await fireEvent.click(await screen.findByRole('button', { name: '删除通话 10010' }))
    expect(screen.getByRole('dialog', { name: '确认删除通话' })).toBeInTheDocument()
    await fireEvent.click(screen.getByRole('button', { name: '确认仅删除服务器通话记录' }))
    expect(remove).toHaveBeenCalledWith('c1')
  })
  it('权威刷新更新同一通话记录的类型和时长', async () => {
    const load = vi.fn()
      .mockResolvedValueOnce({ items: [{ id: 'same', source_id: '1', number: '10010', cached_name: null, call_type: 'INCOMING', call_date: 100, duration: 0 }], next_cursor: null })
      .mockResolvedValueOnce({ items: [{ id: 'same', source_id: '1', number: '10010', cached_name: null, call_type: 'INCOMING', call_date: 100, duration: 65 }], next_cursor: null })
    render(CallsView, { props: { load } })
    expect(await screen.findByText('未接通')).toBeInTheDocument()
    await fireEvent.click(screen.getByRole('button', { name: '刷新通话记录' }))
    expect(await screen.findByText('01:05')).toBeInTheDocument()
    expect(screen.queryByText('未接通')).not.toBeInTheDocument()
  })

  it('最新通话置顶并按常规通话列表呈现方向、时间和时长', async () => {
    const load = vi.fn().mockResolvedValue({
      items: [
        { id: 'old', source_id: '1', number: '10086', cached_name: null, call_type: 'INCOMING', call_date: 100, duration: 65 },
        { id: 'new', source_id: '2', number: '13800138000', cached_name: '家里', call_type: 'MISSED', occurred_at: 300, duration: 0 },
      ],
      next_cursor: null,
    })
    const { container } = render(CallsView, { props: { load } })

    expect(await screen.findByText('家里')).toBeInTheDocument()
    const rows = [...container.querySelectorAll('.call-card')]
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveClass('is-missed')
    expect(rows[0]).toHaveTextContent('未接来电')
    expect(rows[0]).toHaveTextContent('未接通')
    expect(rows[1]).toHaveTextContent('01:05')
    expect(rows[0].querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })

  it('已有通话时后台刷新不闪成加载态且失败不覆盖列表', async () => {
    let rejectRefresh!: (reason: Error) => void
    const pendingRefresh = new Promise<never>((_, reject) => { rejectRefresh = reject })
    const load = vi.fn()
      .mockResolvedValueOnce({ items: [{ id: 'keep', source_id: '1', number: '10010', cached_name: null, call_type: 'INCOMING', call_date: 100, duration: 5 }], next_cursor: null })
      .mockReturnValueOnce(pendingRefresh)
    render(CallsView, { props: { load } })
    expect(await screen.findByText('10010')).toBeInTheDocument()

    await fireEvent.click(screen.getByRole('button', { name: '刷新通话记录' }))
    expect(screen.getByText('10010')).toBeInTheDocument()
    expect(screen.queryByText('正在读取通话镜像…')).not.toBeInTheDocument()
    rejectRefresh(new Error('网络抖动'))
    expect(await screen.findByText('网络抖动')).toBeInTheDocument()
    expect(screen.getByText('10010')).toBeInTheDocument()
    expect(screen.queryByText('无法读取通话记录')).not.toBeInTheDocument()
  })

  it('空列表手动重新读取后显示可见结果', async () => {
    const load = vi.fn().mockResolvedValue({ items: [], next_cursor: null })
    render(CallsView, { props: { load } })
    await fireEvent.click(await screen.findByRole('button', { name: '重新读取' }))
    expect(await screen.findByText('已刷新，暂未收到新通话')).toBeVisible()
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('按通话类型过滤', async () => {
    const load = vi.fn().mockResolvedValue({ items: [], next_cursor: null })
    render(CallsView, { props: { load } })
    await screen.findByText('还没有通话记录')
    await fireEvent.update(screen.getByLabelText('通话类型'), 'MISSED')
    expect(load).toHaveBeenLastCalledWith(expect.objectContaining({ type: 'MISSED' }))
  })
})

describe('DeviceView', () => {
  it('离线状态显示原因和排查动作', async () => {
    const load = vi.fn().mockResolvedValue({ status: 'OFFLINE', status_reason: '超过 120 分钟未收到心跳', name: '安卓手机', last_seen_at: 1700000000, battery_percent: 20, charging: false, network_type: 'unknown', sms_permission_ok: true, call_log_permission_ok: true, pending_event_count: 3, sync_error_count: 1, app_version: '1.0.0' })
    render(DeviceView, { props: { load } })
    expect(await screen.findByText('OFFLINE')).toBeInTheDocument()
    expect(screen.getByText('超过 120 分钟未收到心跳')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '重新检查状态' })).toBeInTheDocument()
  })

  it('尚未收到真实心跳时不把未知值误报为未充电或权限异常', async () => {
    const load = vi.fn().mockResolvedValue({ status: 'UNKNOWN', status_reason: '尚未收到安卓手机心跳', name: '安卓手机', last_seen_at: null, battery_percent: null, charging: null, network_type: null, sms_permission_ok: false, call_log_permission_ok: false, pending_event_count: 0, sync_error_count: 0, app_version: '' })
    render(DeviceView, { props: { load } })
    expect(await screen.findByText('UNKNOWN')).toBeInTheDocument()
    expect(screen.getByText('尚未收到安卓手机心跳')).toBeInTheDocument()
    expect(screen.queryByText('未充电')).not.toBeInTheDocument()
    expect(screen.queryByText('异常')).not.toBeInTheDocument()
    expect(screen.getAllByText('待同步')).toHaveLength(2)
  })
})

describe('SettingsView', () => {
  it('明确 Web Push 为主通道、Bark 为备用，并说明 iPhone 主屏幕限制', async () => {
    const load = vi.fn().mockResolvedValue({ bark_enabled: false, web_push_enabled: false, vapid_public_key: 'AQID' })
    render(SettingsView, { props: { load, saveBark: vi.fn(), testBark: vi.fn(), enablePush: vi.fn() } })
    expect(await screen.findByText('主通知通道 · iPhone 锁屏与主屏幕通知')).toBeInTheDocument()
    expect(screen.getByText('备用故障提醒通道')).toBeInTheDocument()
    expect(screen.getByText(/Safari.*添加到主屏幕.*才能接收锁屏通知/)).toBeInTheDocument()
  })

  it('保存两端链路名称到本机并允许空白回退默认值', async () => {
    const load = vi.fn().mockResolvedValue({ bark_enabled: false, web_push_enabled: false, vapid_public_key: 'AQID' })
    render(SettingsView, { props: { load, saveBark: vi.fn(), testBark: vi.fn(), enablePush: vi.fn() } })

    const source = await screen.findByLabelText('安卓端名称') as HTMLInputElement
    const target = screen.getByLabelText('iPhone 端名称') as HTMLInputElement
    expect(source.value).toBe('安卓')
    expect(target.value).toBe('iPhone')
    await fireEvent.update(source, ' 家里手机 ')
    await fireEvent.update(target, ' ')
    await fireEvent.click(screen.getByRole('button', { name: '保存链路名称' }))

    expect(source.value).toBe('家里手机')
    expect(target.value).toBe('iPhone')
    expect(localStorage.getItem('farbridge.relay.source-label')).toBe('家里手机')
    expect(localStorage.getItem('farbridge.relay.target-label')).toBe('iPhone')
    expect(screen.getByText('链路名称已保存：家里手机 → iPhone')).toBeVisible()
  })

  it('Bark 设备码眼睛切换显示状态时不清空输入', async () => {
    const load = vi.fn().mockResolvedValue({ bark_enabled: false, web_push_enabled: false, vapid_public_key: 'AQID' })
    render(SettingsView, { props: { load, saveBark: vi.fn(), testBark: vi.fn(), enablePush: vi.fn() } })
    const secret = await screen.findByLabelText('Bark 设备码或完整地址') as HTMLInputElement
    await fireEvent.update(secret, 'https://api.day.app/private-device-key')
    expect(secret.type).toBe('password')
    await fireEvent.click(screen.getByRole('button', { name: '显示 Bark 设备码' }))
    expect(secret.type).toBe('text')
    expect(secret.value).toBe('https://api.day.app/private-device-key')
    await fireEvent.click(screen.getByRole('button', { name: '隐藏 Bark 设备码' }))
    expect(secret.type).toBe('password')
    expect(secret.value).toBe('https://api.day.app/private-device-key')
  })

  it('Bark 密钥通过写入接口保存后立即发送测试且不回显', async () => {
    const load = vi.fn().mockResolvedValue({ bark_enabled: true, web_push_enabled: false, vapid_public_key: 'AQID' })
    const saveBark = vi.fn().mockResolvedValue(undefined)
    const testBark = vi.fn().mockResolvedValue(undefined)
    render(SettingsView, { props: { load, saveBark, testBark, enablePush: vi.fn() } })
    expect(await screen.findByText('Bark 已启用')).toBeInTheDocument()
    const secret = screen.getByLabelText('Bark 设备码或完整地址') as HTMLInputElement
    await fireEvent.update(secret, 'https://api.day.app/private-device-key')
    await fireEvent.click(screen.getByRole('button', { name: '保存并发送 Bark 测试' }))
    expect(saveBark).toHaveBeenCalledWith('https://api.day.app/private-device-key', true)
    expect(testBark).toHaveBeenCalledOnce()
    await waitFor(() => expect(secret.value).toBe(''))
    expect(document.body.textContent).not.toContain('private-device-key')
    expect(screen.getByRole('button', { name: '发送 Bark 测试' })).toBeInTheDocument()
  })
})
