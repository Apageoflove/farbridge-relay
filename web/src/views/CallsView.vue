<!-- 呈现通话镜像、类型筛选、稳定分页和 SSE 收敛。 -->
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { deleteCall, listCalls } from '../api/calls'
import CallCard from '../components/CallCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { createSseController, type MirrorChange } from '../composables/useSse'
import type { CallPage, CallQuery, CallRecord, CallType } from '../types/call'
import { sortCallsLatestFirst } from '../utils/recordOrdering'

const props = withDefaults(defineProps<{ load?: (query: CallQuery) => Promise<CallPage>; remove?: (id: string) => Promise<void> }>(), { load: listCalls, remove: deleteCall })
const calls = ref<CallRecord[]>([])
const selectedType = ref<CallType | ''>('')
const nextCursor = ref<string | null>(null)
const loading = ref(true)
const loadedOnce = ref(false)
const loadingMore = ref(false)
const error = ref('')
const announcement = ref('')
const pendingDelete = ref<CallRecord | null>(null)

/** 静默刷新时复用同 ID 对象，保证通话结束后的时长能原位更新。 */
function reconcileCalls(incoming: CallRecord[]): CallRecord[] {
  const existing = new Map(calls.value.map((item) => [item.id, item]))
  return incoming.map((item) => {
    const current = existing.get(item.id)
    if (!current) return item
    Object.assign(current, item)
    return current
  })
}

/** 从头读取当前类型的通话镜像，并在用户主动刷新时给出可见结果。 */
async function refresh(showOutcome = false): Promise<void> {
  loading.value = !loadedOnce.value && calls.value.length === 0
  error.value = ''
  if (showOutcome) announcement.value = ''
  try {
    const page = await props.load({ type: selectedType.value, limit: 30 })
    calls.value = sortCallsLatestFirst(reconcileCalls(page.items))
    nextCursor.value = page.next_cursor
    announcement.value = showOutcome
      ? page.items.length === 0
        ? '已刷新，暂未收到新通话'
        : `已刷新，共收到 ${page.items.length} 条通话`
      : `已读取 ${page.items.length} 条通话记录`
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '网络不可用'
  } finally {
    loadedOnce.value = true
    loading.value = false
  }
}

/** 使用下一页 cursor 追加通话记录。 */
async function loadMore(): Promise<void> {
  if (!nextCursor.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const page = await props.load({ type: selectedType.value, cursor: nextCursor.value, limit: 30 })
    calls.value = sortCallsLatestFirst([...calls.value, ...page.items])
    nextCursor.value = page.next_cursor
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法加载下一页'
  } finally { loadingMore.value = false }
}

/** 根据变化类型局部删除或回源刷新。 */
function onMirrorChange(change: MirrorChange): void {
  if (change.entity !== 'call') return
  if (change.action === 'deleted' && change.sourceId) calls.value = calls.value.filter((item) => item.source_id !== change.sourceId)
  else void refresh()
}

/** 二次确认后只删除服务器镜像记录。 */
async function confirmDelete(): Promise<void> {
  const target = pendingDelete.value
  if (!target) return
  try {
    await props.remove(target.id)
    calls.value = calls.value.filter((item) => item.id !== target.id)
    pendingDelete.value = null
    announcement.value = '已仅从服务器列表删除该通话'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '删除失败'
    announcement.value = `删除失败：${error.value}`
  }
}

/** 复制真实通话号码，并向可见区域与辅助技术同步播报结果。 */
async function copyNumber(number: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(number)
    announcement.value = '电话号码已复制'
  } catch {
    announcement.value = '复制电话号码失败'
  }
}

const sse = typeof EventSource === 'undefined' ? null : createSseController(onMirrorChange, refresh)
const visibilityHandler = () => sse?.onVisibilityChange()
watch(selectedType, () => void refresh())
onMounted(() => { void refresh(); sse?.start(); document.addEventListener('visibilitychange', visibilityHandler) })
onBeforeUnmount(() => { sse?.stop(); document.removeEventListener('visibilitychange', visibilityHandler) })
</script>

<template>
  <section class="page-view" aria-labelledby="calls-title">
    <header class="page-heading"><div><p class="eyebrow">CALL LOG</p><h1 id="calls-title">通话</h1></div><button class="icon-button" type="button" aria-label="刷新通话记录" @click="refresh(true)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 7v5h-5M4 17v-5h5M6.2 8.5A7 7 0 0118.5 7M17.8 15.5A7 7 0 015.5 17"/></svg></button></header>
    <label class="select-field"><span>通话类型</span><select v-model="selectedType"><option value="">全部</option><option value="MISSED">未接</option><option value="INCOMING">呼入</option><option value="OUTGOING">呼出</option><option value="REJECTED">拒接</option><option value="BLOCKED">已拦截</option></select></label>
    <p class="retention-note"><strong>服务器最多保留30天</strong><span>；删除不会影响安卓手机的原通话记录。</span></p>
    <p class="sr-live visible-live" aria-live="polite">{{ announcement }}</p>
    <div v-if="loading" class="loading-state" role="status"><span></span>正在读取通话镜像…</div>
    <EmptyState v-else-if="error && calls.length === 0" title="无法读取通话记录" :detail="`${error}。确认服务器可用后重试。`" action-label="重试" @action="refresh" />
    <EmptyState v-else-if="calls.length === 0" title="还没有通话记录" detail="一加产生通话记录并完成同步后会出现在这里。" action-label="重新读取" @action="refresh(true)" />
    <template v-else>
      <p v-if="error" class="error-banner" role="status">{{ error }}</p>
      <div class="log-list"><CallCard v-for="call in calls" :key="call.id" :call="call" @copy="copyNumber" @request-delete="pendingDelete = call" /></div>
    </template>
    <button v-if="nextCursor && !loading" class="secondary-button load-more" type="button" :disabled="loadingMore" @click="loadMore">{{ loadingMore ? '正在加载…' : '加载更多' }}</button>
    <div v-if="pendingDelete" class="confirm-scrim" role="presentation" @click.self="pendingDelete = null">
      <section class="confirm-dialog" role="dialog" aria-modal="true" aria-label="确认删除通话">
        <h2>仅删除服务器记录？</h2><p>安卓手机里的原通话记录不会被删除。</p>
        <div class="confirm-actions"><button class="secondary-button" type="button" @click="pendingDelete = null">取消</button><button class="primary-button danger-button" type="button" aria-label="确认仅删除服务器通话记录" @click="confirmDelete">确认删除</button></div>
      </section>
    </div>
  </section>
</template>
