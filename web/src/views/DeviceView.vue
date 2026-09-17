<!-- 用中继链路和可操作原因呈现一加设备健康状态。 -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getDevice } from '../api/device'
import EmptyState from '../components/EmptyState.vue'
import RelaySpine from '../components/RelaySpine.vue'
import { createSseController, type MirrorChange } from '../composables/useSse'
import type { DeviceHealth } from '../types/device'

const props = withDefaults(defineProps<{ load?: () => Promise<DeviceHealth> }>(), { load: getDevice })
const device = ref<DeviceHealth | null>(null)
const loading = ref(true)
const error = ref('')
const announcement = ref('')

/** 读取服务器计算后的设备状态及明确原因。 */
async function refresh(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    device.value = await props.load()
    announcement.value = `设备状态 ${device.value.status}`
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '网络不可用'
  } finally { loading.value = false }
}

/** 仅在设备事件到达时刷新健康汇总。 */
function onMirrorChange(change: MirrorChange): void {
  if (change.entity === 'device') void refresh()
}

/** 把秒级时间戳显示为本地日期时间。 */
function timeLabel(timestamp: number | null | undefined): string {
  if (!timestamp) return '尚未收到'
  return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(timestamp * 1000))
}

const reason = computed(() => device.value?.status_reason || (device.value?.status === 'ONLINE' ? '链路心跳正常' : '请检查一加网络和后台权限'))
const awaitingHeartbeat = computed(() => device.value?.status === 'UNKNOWN')
const sse = typeof EventSource === 'undefined' ? null : createSseController(onMirrorChange, refresh)
const visibilityHandler = () => sse?.onVisibilityChange()
onMounted(() => { void refresh(); sse?.start(); document.addEventListener('visibilitychange', visibilityHandler) })
onBeforeUnmount(() => { sse?.stop(); document.removeEventListener('visibilitychange', visibilityHandler) })
</script>

<template>
  <section class="page-view" aria-labelledby="device-title">
    <header class="page-heading"><div><p class="eyebrow">RELAY HEALTH</p><h1 id="device-title">设备</h1></div><button class="icon-button" type="button" aria-label="重新检查状态" @click="refresh"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 7v5h-5M4 17v-5h5M6.2 8.5A7 7 0 0118.5 7M17.8 15.5A7 7 0 015.5 17"/></svg></button></header>
    <p class="sr-live" aria-live="polite">{{ announcement }}</p>
    <div v-if="loading" class="loading-state" role="status"><span></span>正在检查中继链路…</div>
    <EmptyState v-else-if="error" title="无法读取设备状态" :detail="`${error}。确认服务器在线后重试。`" action-label="重试" @action="refresh" />
    <template v-else-if="device">
      <section class="device-hero" :class="device.status.toLowerCase()">
        <div class="status-row"><div><p>{{ device.name }}</p><strong>{{ device.status }}</strong></div><span class="status-light" aria-hidden="true"></span></div>
        <p class="status-reason">{{ reason }}</p>
        <RelaySpine :status="device.status" />
      </section>
      <section class="metric-grid" aria-label="设备遥测">
        <article><span>最后心跳</span><strong>{{ timeLabel(device.last_seen_at) }}</strong></article>
        <article><span>电量</span><strong>{{ device.battery_percent ?? '—' }}<small v-if="device.battery_percent !== null">%</small></strong></article>
        <article><span>供电</span><strong>{{ awaitingHeartbeat ? '—' : device.charging ? '充电中' : '未充电' }}</strong></article>
        <article><span>网络</span><strong>{{ awaitingHeartbeat ? '—' : device.network_type || '未知' }}</strong></article>
      </section>
      <section class="health-list" aria-label="同步健康">
        <div><span>SMS 权限</span><b :class="awaitingHeartbeat ? '' : device.sms_permission_ok ? 'ok' : 'bad'">{{ awaitingHeartbeat ? '待同步' : device.sms_permission_ok ? 'OK' : '异常' }}</b></div>
        <div><span>通话记录权限</span><b :class="awaitingHeartbeat ? '' : device.call_log_permission_ok ? 'ok' : 'bad'">{{ awaitingHeartbeat ? '待同步' : device.call_log_permission_ok ? 'OK' : '异常' }}</b></div>
        <div><span>待同步事件</span><b>{{ device.pending_event_count }}</b></div>
        <div><span>同步错误</span><b :class="device.sync_error_count ? 'bad' : 'ok'">{{ device.sync_error_count || 0 }}</b></div>
        <div><span>App 版本</span><b>{{ device.app_version || '—' }}</b></div>
      </section>
    </template>
  </section>
</template>
