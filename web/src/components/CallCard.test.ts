// 验证通话卡始终使用真实号码提供可访问的复制操作。
import { fireEvent, render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import CallCard from './CallCard.vue'

const baseCall = { id: 'c1', source_id: 's1', number: '13800138000', cached_name: '家里', call_type: 'INCOMING' as const, call_date: 100, duration: 5 }

describe('CallCard', () => {
  it('有备注名时仍可复制真实电话号码', async () => {
    const { emitted } = render(CallCard, { props: { call: baseCall } })
    const button = screen.getByRole('button', { name: '复制电话号码 13800138000' })
    expect(button).toBeInTheDocument()
    expect(button).toHaveClass('copy-number-button')
    await fireEvent.click(button)
    expect(emitted().copy).toEqual([['13800138000']])
  })

  it('没有真实号码时不显示复制按钮', () => {
    render(CallCard, { props: { call: { ...baseCall, number: '', cached_name: null } } })
    expect(screen.queryByRole('button', { name: /复制电话号码/ })).not.toBeInTheDocument()
  })
})
