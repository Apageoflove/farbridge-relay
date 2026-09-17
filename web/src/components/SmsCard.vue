<!-- 显示可展开的短信，并提供 iOS 右滑与按钮操作入口。 -->
<script setup lang="ts">
import { ref } from 'vue'
import type { SmsMessage } from '../types/sms'

const emit = defineEmits<{ copy: [code: string]; favorite: []; pin: []; requestDelete: [] }>()
const props = defineProps<{ message: SmsMessage }>()
const message = props.message
const expanded = ref(false)
const actionsOpen = ref(false)
let pointerStartX: number | null = null
let suppressNextClick = false

/** 缺少短信专用时间时回退到通用事件时间。 */
function timestamp(): number { return Number.isFinite(message.received_at) && Number(message.received_at) > 0 ? Number(message.received_at) : Number(message.occurred_at) || 0 }
/** 从发送方提取稳定的头像字符。 */
function avatarLabel(sender: string): string { return (sender.trim().replace(/^\+/, '')[0] || '?').toUpperCase() }
/** 使用 iPhone 当前时区展示紧凑时间。 */
function formatTime(value: number): string { return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value * 1000)) }
/** 记录指针起点，用位移阈值区分滑动和点按。 */
function startPointer(event: PointerEvent): void { pointerStartX = event.clientX }
/** 右滑打开、左滑收回操作栏，并抑制滑动后的误点击。 */
function finishPointer(event: PointerEvent): void {
  if (pointerStartX !== null) {
    const delta = event.clientX - pointerStartX
    if (delta >= 56) { actionsOpen.value = true; suppressNextClick = true }
    else if (delta <= -56 && actionsOpen.value) { actionsOpen.value = false; suppressNextClick = true }
  }
  pointerStartX = null
}
/** 操作栏打开时点卡片优先收回，其余情况才切换短信全文。 */
function toggleExpanded(): void {
  if (suppressNextClick) { suppressNextClick = false; return }
  if (actionsOpen.value) { actionsOpen.value = false; return }
  expanded.value = !expanded.value
}
/** 操作提交后立即收回操作栏，避免卡片留在偏移状态。 */
function togglePin(): void { actionsOpen.value = false; emit('pin') }
function toggleFavorite(): void { actionsOpen.value = false; emit('favorite') }
function requestDelete(): void { actionsOpen.value = false; emit('requestDelete') }
</script>

<template>
  <div class="sms-swipe-shell" data-testid="sms-swipe-row" @pointerdown="startPointer" @pointerup="finishPointer" @pointercancel="pointerStartX = null">
    <div class="sms-actions" :class="{ 'is-open': actionsOpen }" role="group" aria-label="短信操作" data-testid="sms-actions" :aria-hidden="!actionsOpen">
      <button type="button" :aria-label="message.pinned ? '取消置顶短信' : '置顶短信'" :aria-pressed="Boolean(message.pinned)" :tabindex="actionsOpen ? 0 : -1" @click="togglePin">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 4h8l-1 6 3 3H6l3-3zM12 13v7" /></svg><span>{{ message.pinned ? '取消置顶' : '置顶' }}</span>
      </button>
      <button type="button" :aria-label="message.favorite ? '取消收藏短信' : '收藏短信'" :aria-pressed="Boolean(message.favorite)" :tabindex="actionsOpen ? 0 : -1" @click="toggleFavorite">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l2.7 5.5 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z" /></svg><span>{{ message.favorite ? '已收藏' : '收藏' }}</span>
      </button>
      <button type="button" class="danger-action" aria-label="删除短信" :tabindex="actionsOpen ? 0 : -1" @click="requestDelete">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5" /></svg><span>删除</span>
      </button>
    </div>
    <article class="communication-row sms-card" :class="{ 'is-expanded': expanded, 'has-actions': actionsOpen, 'is-pinned': message.pinned, 'is-favorite': message.favorite }" @click="toggleExpanded">
      <span class="contact-avatar" aria-hidden="true">{{ avatarLabel(message.sender || '') }}</span>
      <div class="communication-main">
        <button class="message-toggle" type="button" :aria-label="expanded ? '收起短信全文' : '展开短信全文'" :aria-expanded="expanded" @click.stop="toggleExpanded">
          <span class="communication-heading">
            <strong><span v-if="message.pinned" class="state-mark">置顶</span><span v-if="message.favorite" class="state-mark">收藏</span>{{ message.sender || '未知发送方' }}</strong>
            <time :datetime="new Date(timestamp() * 1000).toISOString()">{{ formatTime(timestamp()) }}</time>
          </span>
          <span class="message-preview" :class="{ expanded }">{{ message.body || '（空短信）' }}</span>
        </button>
        <button v-if="expanded && message.otp" class="otp-chip" type="button" :aria-label="`复制验证码 ${message.otp}`" @click.stop="emit('copy', message.otp)">
          <span>验证码</span><b>{{ message.otp }}</b><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 8V5h11v11h-3M5 8h11v11H5z" /></svg>
        </button>
      </div>
      <button class="sms-manage-button" type="button" :aria-label="actionsOpen ? '收起短信操作' : '管理短信'" :aria-expanded="actionsOpen" @click.stop="actionsOpen = !actionsOpen">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/></svg>
      </button>
    </article>
  </div>
</template>
