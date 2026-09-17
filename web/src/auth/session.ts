// 管理仅驻留内存的 Cookie 会话状态和 CSRF token。
import { readonly, ref, type Ref } from 'vue'
import { apiFetch, setCsrfToken } from '../api/http'

type ApiCaller = <T>(path: string, options?: RequestInit) => Promise<T>
interface AuthResponse { authenticated?: boolean; csrf_token?: string }

/** 创建可注入 API 的会话状态，方便页面与测试共享同一逻辑。 */
export function createSessionStore(api: ApiCaller = apiFetch) {
  const authenticated = ref(false)
  const initialized = ref(false)

  /** 使用服务端 HttpOnly Cookie 建立会话。 */
  async function login(username: string, password: string): Promise<void> {
    const result = await api<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    setCsrfToken(result.csrf_token || null)
    authenticated.value = result.authenticated !== false
    initialized.value = true
  }

  /** 查询 Cookie 会话并把 CSRF token 留在运行时内存。 */
  async function restore(): Promise<void> {
    try {
      const result = await api<AuthResponse>('/auth/session')
      setCsrfToken(result.csrf_token || null)
      authenticated.value = result.authenticated === true
    } catch {
      setCsrfToken(null)
      authenticated.value = false
    } finally {
      initialized.value = true
    }
  }

  /** 在服务端撤销会话并清空所有内存认证信息。 */
  async function logout(): Promise<void> {
    try {
      await api<void>('/auth/logout', { method: 'POST' })
    } finally {
      setCsrfToken(null)
      authenticated.value = false
      initialized.value = true
    }
  }

  /** 将认证状态同步到路由层且不暴露可变 ref。 */
  function markUnauthorized(): void {
    setCsrfToken(null)
    authenticated.value = false
    initialized.value = true
  }

  return {
    authenticated: readonly(authenticated) as Readonly<Ref<boolean>>,
    initialized: readonly(initialized) as Readonly<Ref<boolean>>,
    login,
    restore,
    logout,
    markUnauthorized,
  }
}

export const sessionStore = createSessionStore()
