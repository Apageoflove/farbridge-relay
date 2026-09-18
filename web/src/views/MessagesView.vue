<!-- 呈现服务器当前短信镜像、搜索筛选、复制和实时删除。 -->
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { deleteMessage, listMessages, updateMessageState } from '../api/messages'
import EmptyState from '../components/EmptyState.vue'
import SmsCard from '../components/SmsCard.vue'
import { createSseController, type MirrorChange } from '../composables/useSse'
import type { CursorPage, MessageQuery, MessageStatePatch, SmsMessage } from '../types/sms'
import { sortMessagesLatestFirst } from '../utils/recordOrdering'

const props = withDefaults(defineProps<{
  load?: (query: MessageQuery) => Promise<CursorPage<SmsMessage>>
  update?: (id: string, patch: MessageStatePatch) => Promise<SmsMessage>
  remove?: (id: string) => Promise<void>
}>(), { load: listMessages, update: updateMessageState, remove: deleteMessage })
const messages = ref<SmsMessage[]>([])
const nextCursor = ref<string | null>(null)
const q = ref('')
const sender = ref('')
const loading = ref(true)
const loadedOnce = ref(false)
const loadingMore = ref(false)
const refreshing = ref(false)
const error = ref('')
const announcement = ref('')
const refreshFeedbackVisible = ref(false)
const pendingDelete = ref<SmsMessage | null>(null)

/** 静默刷新时复用同 ID 对象，使已挂载卡片收到最新字段而无需闪烁重建。 */
function reconcileMessages(incoming: SmsMessage[]): SmsMessage[] {
  const existing = new Map(messages.value.map((item) => [item.id, item]))
  return incoming.map((item) => {
    const current = existing.get(item.id)
    if (!current) return item
    Object.assign(current, item)
    return current
  })
}

/** 从头读取服务器权威镜像，避免恢复前台后依赖遗漏的 SSE。 */
async function refresh(showOutcome = false, preserveExisting = false): Promise<void> {
  if (refreshing.value) return
  refreshing.value = true
  loading.value = !loadedOnce.value && messages.value.length === 0
  error.value = ''
  if (showOutcome) {
    refreshFeedbackVisible.value = true
    announcement.value = '正在刷新消息…'
  }
  try {
    const page = await props.load({ q: q.value || undefined, sender: sender.value || undefined, cursor: undefined, limit: 30 })
    const reconciled = reconcileMessages(page.items)
    const seen = new Set(reconciled.map((item) => item.id))
    const merged = preserveExisting ? [...reconciled, ...messages.value.filter((item) => !seen.has(item.id))] : reconciled
    messages.value = sortMessagesLatestFirst(merged)
    nextCursor.value = page.next_cursor
    announcement.value = showOutcome ? `已刷新，共收到 ${messages.value.length} 条消息` : `已读取 ${page.items.length} 条消息`
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '网络不可用'
    if (showOutcome) announcement.value = `刷新失败：${error.value}`
  } finally {
    loadedOnce.value = true
    loading.value = false
    refreshing.value = false
  }
}

/** 应用筛选时重新建立结果集，避免混入上一组条件的历史记录。 */
function applyFilters(): void {
  refreshFeedbackVisible.value = false
  void refresh(false, false)
}

/** 使用服务端返回的稳定 cursor 追加下一页。 */
async function loadMore(): Promise<void> {
  if (!nextCursor.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const page = await props.load({ q: q.value || undefined, sender: sender.value || undefined, cursor: nextCursor.value, limit: 30 })
    messages.value = sortMessagesLatestFirst([...messages.value, ...page.items])
    nextCursor.value = page.next_cursor
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法加载下一页'
  } finally {
    loadingMore.value = false
  }
}

/** 复制 OTP 并通过 ARIA live 给出不泄露正文的反馈。 */
async function copyCode(code: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(code)
    announcement.value = '验证码已复制'
  } catch {
    announcement.value = '复制失败，请长按验证码手动复制'
  }
}

/** 乐观更新收藏或置顶，服务器拒绝时精确回滚原值。 */
async function updateState(message: SmsMessage, patch: MessageStatePatch): Promise<void> {
  const previous = { favorite: Boolean(message.favorite), pinned: Boolean(message.pinned) }
  Object.assign(message, patch)
  messages.value = sortMessagesLatestFirst(messages.value)
  try {
    const saved = await props.update(message.id, patch)
    if (saved) Object.assign(message, saved)
    messages.value = sortMessagesLatestFirst(messages.value)
    announcement.value = patch.pinned !== undefined ? (message.pinned ? '已置顶短信' : '已取消置顶') : (message.favorite ? '已收藏短信' : '已取消收藏')
  } catch (cause) {
    message.favorite = previous.favorite
    message.pinned = previous.pinned
    messages.value = sortMessagesLatestFirst(messages.value)
    error.value = cause instanceof Error ? cause.message : '保存失败'
    announcement.value = `保存失败：${error.value}`
  }
}

/** 二次确认后只删除服务器镜像，不影响安卓原短信。 */
async function confirmDelete(): Promise<void> {
  const target = pendingDelete.value
  if (!target) return
  try {
    await props.remove(target.id)
    messages.value = messages.value.filter((item) => item.id !== target.id)
    pendingDelete.value = null
    announcement.value = '已仅从服务器列表删除该短信'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '删除失败'
    announcement.value = `删除失败：${error.value}`
  }
}

/** 所有短信变化都回源刷新，避免收藏短信被安卓删除事件误移除。 */
function onMirrorChange(change: MirrorChange): void {
  if (change.entity !== 'sms') return
  void refresh(false, false)
}

// SSE、恢复前台和 5 秒兜底刷新以服务器首页为权威集，及时移除过期或已删除记录。
const authoritativeRefresh = () => refresh(false, false)
const sse = typeof EventSource === 'undefined' ? null : createSseController(onMirrorChange, authoritativeRefresh)
const visibilityHandler = () => sse?.onVisibilityChange()
onMounted(() => {
  void refresh()
  sse?.start()
  document.addEventListener('visibilitychange', visibilityHandler)
})
onBeforeUnmount(() => {
  sse?.stop()
  document.removeEventListener('visibilitychange', visibilityHandler)
})
</script>

<template>
  <section class="page-view" aria-labelledby="messages-title">
    <header class="page-heading">
      <div><p class="eyebrow">SERVER MIRROR</p><h1 id="messages-title">消息</h1></div>
      <button class="icon-button" type="button" aria-label="刷新消息" :aria-busy="refreshing" :disabled="refreshing" @click="refresh(true, true)"><svg :class="{ spinning: refreshing }" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 7v5h-5M4 17v-5h5M6.2 8.5A7 7 0 0118.5 7M17.8 15.5A7 7 0 015.5 17"/></svg></button>
    </header>
    <form class="filter-panel" @submit.prevent="applyFilters">
      <label class="search-field"><span>搜索消息</span><div><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6"/><path d="M16 16l4 4"/></svg><input v-model.trim="q" type="search" placeholder="正文或验证码" /></div></label>
      <label><span>发送方</span><input v-model.trim="sender" placeholder="例如 10086" /></label>
      <button class="secondary-button filter-action" type="submit">应用筛选</button>
    </form>
    <p class="sr-live" :class="{ 'visible-live': refreshFeedbackVisible }" aria-live="polite">{{ announcement }}</p>
    <div v-if="loading" class="loading-state" role="status"><span></span>正在读取服务器镜像…</div>
    <EmptyState v-else-if="error && messages.length === 0" title="无法读取消息" :detail="`${error}。确认服务器可用后重试。`" action-label="重试" @action="refresh" />
    <EmptyState v-else-if="messages.length === 0" title="还没有镜像消息" detail="新短信到达安卓手机并完成同步后会出现在这里。" action-label="重新读取" @action="refresh" />
    <template v-else>
      <p v-if="error" class="error-banner" role="status">{{ error }}</p>
      <p class="retention-note"><strong>服务器最多保留30天</strong><span>；已收藏短信除非你主动删除，否则永久保留。</span></p>
      <div class="log-list"><SmsCard v-for="message in messages" :key="message.id" :message="message" @copy="copyCode" @favorite="updateState(message, { favorite: !message.favorite })" @pin="updateState(message, { pinned: !message.pinned })" @request-delete="pendingDelete = message" /></div>
    </template>
    <button v-if="nextCursor && !loading" class="secondary-button load-more" type="button" :disabled="loadingMore" @click="loadMore">{{ loadingMore ? '正在加载…' : '加载更多' }}</button>
    <div v-if="pendingDelete" class="confirm-scrim" role="presentation" @click.self="pendingDelete = null">
      <section class="confirm-dialog" role="dialog" aria-modal="true" aria-label="确认删除短信">
        <h2>仅删除服务器记录？</h2>
        <p>安卓手机里的原短信不会被删除。</p>
        <div class="confirm-actions"><button class="secondary-button" type="button" @click="pendingDelete = null">取消</button><button class="primary-button danger-button" type="button" aria-label="确认仅删除服务器记录" @click="confirmDelete">确认删除</button></div>
      </section>
    </div>
  </section>
</template>
