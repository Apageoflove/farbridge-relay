<!-- 提供不记忆密码的 Cookie 会话登录入口。 -->
<script setup lang="ts">
import { ref } from 'vue'
import { sessionStore } from '../auth/session'

const props = withDefaults(defineProps<{ login?: (username: string, password: string) => Promise<void> }>(), {
  login: sessionStore.login,
})
const emit = defineEmits<{ signedIn: [] }>()
const username = ref('')
const password = ref('')
const passwordVisible = ref(false)
const busy = ref(false)
const error = ref('')

/** 校验并提交凭据，结束后立即清空密码字段。 */
async function submit(): Promise<void> {
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  busy.value = true
  error.value = ''
  try {
    await props.login(username.value, password.value)
    password.value = ''
    passwordVisible.value = false
    emit('signedIn')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败，请检查账号后重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel" aria-labelledby="login-title">
      <div class="brand-lockup">
        <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M5 7h22v18H5zM10 12h12M10 17h8"/></svg>
        <span>远桥 · FARBRIDGE</span>
      </div>
      <p class="eyebrow">PRIVATE RELAY</p>
      <h1 id="login-title">进入通信镜像</h1>
      <p class="intro">查看安卓手机安全同步的短信、验证码和通话记录。</p>
      <form @submit.prevent="submit">
        <label>用户名<input v-model.trim="username" name="username" autocomplete="username" inputmode="text" /></label>
        <label for="login-password">密码</label>
        <div class="secret-field">
          <input id="login-password" v-model="password" name="password" :type="passwordVisible ? 'text' : 'password'" autocomplete="current-password" />
          <button class="secret-toggle" type="button" :aria-label="passwordVisible ? '隐藏密码' : '显示密码'" @click="passwordVisible = !passwordVisible">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6M12 9a3 3 0 100 6 3 3 0 000-6" /></svg>
          </button>
        </div>
        <p v-if="error" role="alert" class="error-banner">{{ error }}</p>
        <button class="primary-button" type="submit" :disabled="busy">{{ busy ? '正在验证…' : error ? '重新登录' : '登录' }}</button>
      </form>
      <p class="privacy-note">账号信息只用于本次安全会话，不保存在浏览器存储中。</p>
    </section>
  </main>
</template>
