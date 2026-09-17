// 定义五个页面并用 Cookie 会话状态保护业务路由。
import { createRouter, createWebHistory } from 'vue-router'
import { sessionStore } from '../auth/session'
import { decideNavigation } from './guards'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/messages' },
    { path: '/login', component: () => import('../views/LoginView.vue') },
    { path: '/messages', component: () => import('../views/MessagesView.vue'), meta: { requiresAuth: true } },
    { path: '/calls', component: () => import('../views/CallsView.vue'), meta: { requiresAuth: true } },
    { path: '/device', component: () => import('../views/DeviceView.vue'), meta: { requiresAuth: true } },
    { path: '/settings', component: () => import('../views/SettingsView.vue'), meta: { requiresAuth: true } },
    { path: '/:pathMatch(.*)*', redirect: '/messages' },
  ],
})

router.beforeEach(async (to) => {
  if (!sessionStore.initialized.value) await sessionStore.restore()
  return decideNavigation(sessionStore.authenticated.value, to.fullPath, to.meta.requiresAuth === true)
})

export default router
