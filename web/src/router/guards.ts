// 将认证状态转换为无副作用、可测试的路由决策。
import type { RouteLocationRaw } from 'vue-router'

/** 保护业务页，同时避免认证用户重复进入登录页。 */
export function decideNavigation(authenticated: boolean, path: string, requiresAuth: boolean): RouteLocationRaw | true {
  if (requiresAuth && !authenticated) return { path: '/login', query: { redirect: path } }
  if (path === '/login' && authenticated) return '/messages'
  return true
}
