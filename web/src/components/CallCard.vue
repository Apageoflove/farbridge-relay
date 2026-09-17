<!-- 显示单条通话镜像并将类型、时长转为易读文本。 -->
<script setup lang="ts">
import type { CallRecord, CallType } from '../types/call'
const emit = defineEmits<{ copy: [number: string]; requestDelete: [] }>()
const props = defineProps<{ call: CallRecord }>()
const call = props.call

const typeLabels: Record<CallType, string> = { INCOMING: '呼入电话', OUTGOING: '呼出电话', MISSED: '未接来电', REJECTED: '已拒接', BLOCKED: '已拦截', VOICEMAIL: '语音信箱', UNKNOWN: '未知类型' }

/** 把秒数显示为适合日志扫描的分钟和秒。 */
function durationLabel(seconds: number): string {
  if (!seconds) return '未接通'
  return `${Math.floor(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`
}

/** 使用 iPhone 当前时区格式化通话发生时间。 */
function formatTime(timestamp: number): string {
  return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(timestamp * 1000))
}

/** 缺少通话专用时间时回退到通用事件时间。 */
function timestamp(): number {
  return Number.isFinite(call.call_date) && Number(call.call_date) > 0 ? Number(call.call_date) : Number(call.occurred_at) || 0
}

/** 为不同方向提供常规电话列表中的箭头路径。 */
function directionPath(type: CallType): string {
  if (type === 'OUTGOING') return 'M8 16L16 8M10 8h6v6M7.5 4.5l3 3-2.2 2.2a12 12 0 006 6l2.2-2.2 3 3-1.5 3c-.7.7-1.8 1-2.8.8C9.2 17.8 4.2 12.8 2.7 6.8c-.2-1 .1-2.1.8-2.8z'
  return 'M16 8L8 16M14 16H8v-6M7.5 4.5l3 3-2.2 2.2a12 12 0 006 6l2.2-2.2 3 3-1.5 3c-.7.7-1.8 1-2.8.8C9.2 17.8 4.2 12.8 2.7 6.8c-.2-1 .1-2.1.8-2.8z'
}
</script>

<template>
  <article class="communication-row call-card" :class="{ 'is-missed': call.call_type === 'MISSED' }">
    <span class="call-direction" :class="call.call_type.toLowerCase()" aria-hidden="true">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="directionPath(call.call_type)" /></svg>
    </span>
    <div class="communication-main">
      <strong>{{ call.cached_name || call.number || '未知号码' }}</strong>
      <p class="call-summary"><span>{{ typeLabels[call.call_type] }}</span><span v-if="call.cached_name">{{ call.number }}</span><span v-if="call.phone_account_id">{{ call.phone_account_id }}</span></p>
    </div>
    <div class="call-trailing">
      <time :datetime="new Date(timestamp() * 1000).toISOString()">{{ formatTime(timestamp()) }}</time>
      <span>{{ durationLabel(call.duration) }}</span>
    </div>
    <button v-if="call.number" class="copy-number-button" type="button" :aria-label="`复制电话号码 ${call.number}`" @click="emit('copy', call.number)">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 8V5h11v11h-3M5 8h11v11H5z" /></svg>
    </button>
    <button class="row-delete-button" type="button" :aria-label="`删除通话 ${call.cached_name || call.number || '未知号码'}`" @click="emit('requestDelete')">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5" /></svg>
    </button>
  </article>
</template>
