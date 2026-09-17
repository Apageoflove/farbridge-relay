// 验证分页合并后按真实发生时间倒序，并去除重复记录。
import { describe, expect, it } from 'vitest'
import { sortCallsLatestFirst, sortMessagesLatestFirst } from './recordOrdering'

describe('record ordering', () => {
  it('短信优先 received_at 并回退 occurred_at 后按 id 去重', () => {
    const ordered = sortMessagesLatestFirst([
      { id: 'old', source_id: '1', sender: 'A', body: 'old', otp: null, received_at: 100 },
      { id: 'fallback', source_id: '2', sender: 'B', body: 'new', otp: null, occurred_at: 300 },
      { id: 'old', source_id: '1', sender: 'A', body: 'duplicate', otp: null, received_at: 100 },
      { id: 'middle', source_id: '3', sender: 'C', body: 'middle', otp: null, received_at: 200, occurred_at: 999 },
    ])

    expect(ordered.map((item) => item.id)).toEqual(['fallback', 'middle', 'old'])
    expect(ordered.find((item) => item.id === 'old')?.body).toBe('old')
  })

  it('通话优先 call_date 并回退 occurred_at 后按 id 去重', () => {
    const ordered = sortCallsLatestFirst([
      { id: 'old', source_id: '1', number: '1', cached_name: null, call_type: 'INCOMING', call_date: 100, duration: 1 },
      { id: 'fallback', source_id: '2', number: '2', cached_name: null, call_type: 'MISSED', occurred_at: 300, duration: 0 },
      { id: 'old', source_id: '1', number: '1', cached_name: null, call_type: 'INCOMING', call_date: 100, duration: 9 },
      { id: 'middle', source_id: '3', number: '3', cached_name: null, call_type: 'OUTGOING', call_date: 200, occurred_at: 999, duration: 2 },
    ])

    expect(ordered.map((item) => item.id)).toEqual(['fallback', 'middle', 'old'])
    expect(ordered.find((item) => item.id === 'old')?.duration).toBe(1)
  })
})
