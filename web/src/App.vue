<!-- 组织移动端应用壳、顶部中继脊柱和安全区底部导航。 -->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppNav from './components/AppNav.vue'
import RelaySpine from './components/RelaySpine.vue'
import { sessionStore } from './auth/session'

const route = useRoute()
const router = useRouter()
const isLogin = computed(() => route.path === '/login')

/** 登录成功后回到原目标页，且只接受站内绝对路径。 */
function signedIn(): void {
  const requested = typeof route.query.redirect === 'string' ? route.query.redirect : '/messages'
  void router.replace(requested.startsWith('/') && !requested.startsWith('//') ? requested : '/messages')
}

/** 注销完成后清空认证状态并切换登录页。 */
function signedOut(): void {
  sessionStore.markUnauthorized()
  void router.replace('/login')
}
</script>

<template>
  <div class="app-shell" :class="{ 'login-shell': isLogin }">
    <header v-if="!isLogin" class="top-rail">
      <RouterLink to="/device" aria-label="查看中继链路状态"><RelaySpine compact /></RouterLink>
    </header>
    <RouterView v-slot="{ Component }"><component :is="Component" @signed-in="signedIn" @signed-out="signedOut" /></RouterView>
    <AppNav v-if="!isLogin" />
  </div>
</template>
