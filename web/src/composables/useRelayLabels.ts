// 在浏览器本机持久化展示昵称，绝不修改服务器或安卓设备的真实身份。
import { ref } from 'vue'

export const DEFAULT_SOURCE_LABEL = '安卓'
export const DEFAULT_TARGET_LABEL = 'iPhone'
const SOURCE_STORAGE_KEY = 'farbridge.relay.source-label'
const TARGET_STORAGE_KEY = 'farbridge.relay.target-label'

/** 读取昵称时容忍隐私模式或存储被禁用，并把空白值回退为默认文案。 */
function readLabel(key: string, fallback: string): string {
  try { return localStorage.getItem(key)?.trim() || fallback }
  catch { return fallback }
}

const sourceLabel = ref(readLabel(SOURCE_STORAGE_KEY, DEFAULT_SOURCE_LABEL))
const targetLabel = ref(readLabel(TARGET_STORAGE_KEY, DEFAULT_TARGET_LABEL))

/** 暴露跨页面共享的响应式链路昵称。 */
export function useRelayLabels() {
  return { sourceLabel, targetLabel }
}

/** 规范化并保存展示昵称；无法持久化时仍立即更新当前页面。 */
export function saveRelayLabels(source: string, target: string): { source: string; target: string } {
  const normalized = {
    source: source.trim() || DEFAULT_SOURCE_LABEL,
    target: target.trim() || DEFAULT_TARGET_LABEL,
  }
  sourceLabel.value = normalized.source
  targetLabel.value = normalized.target
  try {
    localStorage.setItem(SOURCE_STORAGE_KEY, normalized.source)
    localStorage.setItem(TARGET_STORAGE_KEY, normalized.target)
  } catch {
    // Safari 隐私模式可能拒绝持久化；内存状态仍可用于当前会话。
  }
  return normalized
}
