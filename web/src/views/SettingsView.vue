<!-- 管理标准 Web Push，并只展示服务器 Bark 状态而不暴露密钥。 -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getNotificationSettings, saveBarkSettings, sendBarkTest } from '../api/device'
import { apiFetch } from '../api/http'
import { sessionStore } from '../auth/session'
import { enableWebPush } from '../composables/useWebPush'
import { saveRelayLabels, useRelayLabels } from '../composables/useRelayLabels'
import type { NotificationSettings } from '../types/device'

const props = withDefaults(defineProps<{
  load?: () => Promise<NotificationSettings>
  saveBark?: (key: string, privacyMode: boolean) => Promise<void>
  testBark?: () => Promise<void>
  enablePush?: (gesture: boolean, key: string, api: typeof apiFetch) => Promise<void>
}>(), { load: getNotificationSettings, saveBark: saveBarkSettings, testBark: sendBarkTest, enablePush: enableWebPush })
const emit = defineEmits<{ signedOut: [] }>()
const settings = ref<NotificationSettings | null>(null)
const loading = ref(true)
const error = ref('')
const announcement = ref('')
const busy = ref(false)
const barkKey = ref('')
const barkKeyVisible = ref(false)
const showBarkContent = ref(false)
const { sourceLabel, targetLabel } = useRelayLabels()
const sourceLabelInput = ref(sourceLabel.value)
const targetLabelInput = ref(targetLabel.value)

/** 保存纯展示昵称；空白名称回退默认值且不影响真实设备 ID。 */
function configureRelayLabels(): void {
  const saved = saveRelayLabels(sourceLabelInput.value, targetLabelInput.value)
  sourceLabelInput.value = saved.source
  targetLabelInput.value = saved.target
  announcement.value = `链路名称已保存：${saved.source} → ${saved.target}`
}

/** 读取仅含启用状态和 VAPID 公钥的公开设置。 */
async function refresh(): Promise<void> {
  loading.value = true
  error.value = ''
  try { settings.value = await props.load() }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '无法读取设置' }
  finally { loading.value = false }
}

/** 响应按钮手势开启标准 Web Push。 */
async function turnOnPush(): Promise<void> {
  if (!settings.value) return
  busy.value = true
  try {
    await props.enablePush(true, settings.value.vapid_public_key, apiFetch)
    settings.value.web_push_enabled = true
    announcement.value = 'Web Push 已开启'
  } catch (cause) {
    announcement.value = cause instanceof Error ? cause.message : '无法开启通知'
  } finally { busy.value = false }
}

/** 请求服务器发送 Bark 测试，不接触 Bark key。 */
async function runBarkTest(): Promise<void> {
  busy.value = true
  try { await props.testBark(); announcement.value = 'Bark 测试已发送，请检查 iPhone' }
  catch (cause) { announcement.value = cause instanceof Error ? cause.message : 'Bark 测试失败，请检查服务器配置' }
  finally { busy.value = false }
}

/** 保存写入型设备码后立刻测试；成功后清空输入框避免浏览器残留。 */
async function configureBark(): Promise<void> {
  const key = barkKey.value.trim()
  if (!key) { announcement.value = '请填写 Bark 设备码或完整地址'; return }
  busy.value = true
  try {
    await props.saveBark(key, !showBarkContent.value)
    await props.testBark()
    if (settings.value) settings.value.bark_enabled = true
    barkKey.value = ''
    barkKeyVisible.value = false
    announcement.value = 'Bark 已保存并发送测试，请检查 iPhone'
  } catch (cause) {
    announcement.value = cause instanceof Error ? cause.message : 'Bark 保存或测试失败'
  } finally { busy.value = false }
}

/** 撤销服务器会话后通知应用壳返回登录页。 */
async function signOut(): Promise<void> {
  busy.value = true
  try { await sessionStore.logout(); emit('signedOut') }
  finally { busy.value = false }
}

onMounted(() => void refresh())
</script>

<template>
  <section class="page-view" aria-labelledby="settings-title">
    <header class="page-heading"><div><p class="eyebrow">DELIVERY</p><h1 id="settings-title">设置</h1></div></header>
    <p class="sr-live visible-live" aria-live="polite">{{ announcement }}</p>
    <div v-if="loading" class="loading-state" role="status"><span></span>正在读取通知设置…</div>
    <section v-else-if="error" class="error-panel" role="alert"><h2>无法读取设置</h2><p>{{ error }}</p><button class="secondary-button" type="button" @click="refresh">重试</button></section>
    <template v-else-if="settings">
      <section class="settings-card">
        <div class="settings-title"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 9a6 6 0 0112 0c0 7 3 7 3 7H3s3 0 3-7M10 20h4"/></svg><div><h2>Web Push</h2><p>主通知通道 · iPhone 锁屏与主屏幕通知</p></div><span :class="settings.web_push_enabled ? 'enabled' : 'disabled'">{{ settings.web_push_enabled ? '已开启' : '未开启' }}</span></div>
        <p class="settings-detail">只有先用 Safari 将远桥添加到主屏幕，从桌面打开后点击开启，才能接收锁屏通知。通知只负责提醒，历史以当前 PWA 为准。</p>
        <button class="primary-button" type="button" :disabled="busy || settings.web_push_enabled" @click="turnOnPush">{{ settings.web_push_enabled ? 'Web Push 已开启' : '开启 Web Push' }}</button>
      </section>
      <section class="settings-card">
        <div class="settings-title"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 5h14v14H5zM8 9h8M8 13h5"/></svg><div><h2>Bark</h2><p>备用故障提醒通道</p></div><span :class="settings.bark_enabled ? 'enabled' : 'disabled'">{{ settings.bark_enabled ? 'Bark 已启用' : 'Bark 未启用' }}</span></div>
        <p class="settings-detail">从 Bark 首页复制设备码或完整地址。设备码会在服务器数据库中加密保存，本页不会回读或显示它。</p>
        <div class="bark-form">
          <label for="bark-device-key"><span>Bark 设备码或完整地址</span></label>
          <div class="secret-field">
            <input id="bark-device-key" v-model="barkKey" :type="barkKeyVisible ? 'text' : 'password'" autocomplete="off" placeholder="https://api.day.app/你的设备码">
            <button class="secret-toggle" type="button" :aria-label="barkKeyVisible ? '隐藏 Bark 设备码' : '显示 Bark 设备码'" @click="barkKeyVisible = !barkKeyVisible">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6M12 9a3 3 0 100 6 3 3 0 000-6" /></svg>
            </button>
          </div>
          <label for="bark-content-mode"><span>锁屏通知内容</span></label>
          <select id="bark-content-mode" v-model="showBarkContent">
            <option :value="false">仅提示收到验证码（推荐）</option>
            <option :value="true">显示验证码与短信内容</option>
          </select>
          <button class="primary-button full-width" type="button" :disabled="busy || !barkKey.trim()" @click="configureBark">保存并发送 Bark 测试</button>
          <button class="secondary-button full-width" type="button" :disabled="busy || !settings.bark_enabled" @click="runBarkTest">发送 Bark 测试</button>
        </div>
      </section>
      <section class="settings-card">
        <div class="settings-title"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 12h16M16 8l4 4-4 4M8 8l-4 4 4 4"/></svg><div><h2>链路名称</h2><p>自定义顶部的来源与接收端</p></div></div>
        <p class="settings-detail">这些名称只显示在当前浏览器，不会更改安卓设备 ID 或服务器身份。</p>
        <form class="relay-label-form" @submit.prevent="configureRelayLabels">
          <label for="relay-source"><span>安卓端名称</span><input id="relay-source" v-model="sourceLabelInput" maxlength="18" autocomplete="off"></label>
          <span class="relay-form-arrow" aria-hidden="true">→</span>
          <label for="relay-target"><span>iPhone 端名称</span><input id="relay-target" v-model="targetLabelInput" maxlength="18" autocomplete="off"></label>
          <button class="secondary-button full-width" type="submit">保存链路名称</button>
        </form>
      </section>
      <section class="privacy-card"><h2>本机数据边界</h2><p>本机只在 localStorage 保存你设置的链路昵称；短信正文、验证码和通话记录不会写入 localStorage、IndexedDB 或 Cache Storage。</p></section>
      <button class="text-button danger-text full-width" type="button" :disabled="busy" @click="signOut">退出当前会话</button>
    </template>
  </section>
</template>
