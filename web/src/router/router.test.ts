// 验证受保护页面和登录页的路由守卫。
import { describe, expect, it } from 'vitest'
import { decideNavigation } from './guards'

describe('route guard', () => {
  it('未认证访问消息页会跳转登录并保留目标', () => {
    expect(decideNavigation(false, '/messages', true)).toEqual({ path: '/login', query: { redirect: '/messages' } })
  })

  it('已认证访问登录页会进入消息页', () => {
    expect(decideNavigation(true, '/login', false)).toBe('/messages')
  })
})
