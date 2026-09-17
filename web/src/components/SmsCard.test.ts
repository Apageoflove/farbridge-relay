// 验证短信卡的展开、滑动和无障碍操作入口。
import { fireEvent, render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import SmsCard from './SmsCard.vue'

const message = { id: 'm1', source_id: 's1', sender: '10086', body: '这是一条很长的短信正文654321，点击以后应当能够完整展开查看所有内容。', otp: '654321', received_at: 100, favorite: false, pinned: false }

describe('SmsCard', () => {
  it('点击正文可展开全部短信并再次收起', async () => {
    render(SmsCard, { props: { message } })
    const body = screen.getByRole('button', { name: '展开短信全文' })
    expect(body).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('button', { name: '复制验证码 654321' })).not.toBeInTheDocument()
    await fireEvent.click(body)
    const expanded = screen.getByRole('button', { name: '收起短信全文' })
    expect(expanded).toHaveAttribute('aria-expanded', 'true')
    expect(expanded).toHaveTextContent(message.body)
    expect(screen.getByRole('button', { name: '复制验证码 654321' })).toBeInTheDocument()
    await fireEvent.click(expanded)
    expect(screen.getByRole('button', { name: '展开短信全文' })).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('button', { name: '复制验证码 654321' })).not.toBeInTheDocument()
  })

  it('向右滑露出置顶、收藏、删除，且按钮可直接操作', async () => {
    const { emitted } = render(SmsCard, { props: { message } })
    const row = screen.getByTestId('sms-swipe-row')
    await fireEvent(row, Object.assign(new Event('pointerdown', { bubbles: true }), { clientX: 20 }))
    await fireEvent(row, Object.assign(new Event('pointerup', { bubbles: true }), { clientX: 150 }))
    expect(screen.getByRole('group', { name: '短信操作' })).toHaveClass('is-open')
    expect(screen.getByRole('button', { name: '展开短信全文' })).toHaveAttribute('aria-expanded', 'false')
    await fireEvent.click(screen.getByRole('button', { name: '置顶短信' }))
    await fireEvent.click(screen.getByRole('button', { name: '管理短信' }))
    await fireEvent.click(screen.getByRole('button', { name: '收藏短信' }))
    await fireEvent.click(screen.getByRole('button', { name: '管理短信' }))
    await fireEvent.click(screen.getByRole('button', { name: '删除短信' }))
    expect(emitted().pin).toHaveLength(1)
    expect(emitted().favorite).toHaveLength(1)
    expect(emitted().requestDelete).toHaveLength(1)
  })

  it('操作栏打开后点击卡片只收回操作栏，不展开短信', async () => {
    render(SmsCard, { props: { message } })
    await fireEvent.click(screen.getByRole('button', { name: '管理短信' }))
    expect(screen.getByRole('group', { name: '短信操作' })).toHaveClass('is-open')
    await fireEvent.click(screen.getByRole('article'))
    expect(screen.getByTestId('sms-actions')).not.toHaveClass('is-open')
    expect(screen.getByRole('button', { name: '展开短信全文' })).toHaveAttribute('aria-expanded', 'false')
  })

  it('操作栏打开后可向左滑收回', async () => {
    render(SmsCard, { props: { message } })
    const row = screen.getByTestId('sms-swipe-row')
    await fireEvent.click(screen.getByRole('button', { name: '管理短信' }))
    await fireEvent(row, Object.assign(new Event('pointerdown', { bubbles: true }), { clientX: 180 }))
    await fireEvent(row, Object.assign(new Event('pointerup', { bubbles: true }), { clientX: 80 }))
    expect(screen.getByTestId('sms-actions')).not.toHaveClass('is-open')
  })

  it('置顶或收藏后自动收回操作栏', async () => {
    const { emitted } = render(SmsCard, { props: { message } })
    await fireEvent.click(screen.getByRole('button', { name: '管理短信' }))
    const actions = screen.getByRole('group', { name: '短信操作' })
    await fireEvent.click(screen.getByRole('button', { name: '置顶短信' }))
    expect(actions).not.toHaveClass('is-open')
    await fireEvent.click(screen.getByRole('button', { name: '管理短信' }))
    await fireEvent.click(screen.getByRole('button', { name: '收藏短信' }))
    expect(actions).not.toHaveClass('is-open')
    expect(emitted().pin).toHaveLength(1)
    expect(emitted().favorite).toHaveLength(1)
  })
})
