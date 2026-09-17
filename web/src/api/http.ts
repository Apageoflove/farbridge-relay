// 提供同源、Cookie 会话、CSRF、超时和统一错误处理的 API 客户端。
const API_PREFIX = '/api/v1'
const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

let csrfToken: string | null = null
let unauthorizedHandler: (() => void) | undefined

/** 表示服务端或网络层返回的可判断 API 错误。 */
export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** 仅在当前页面内存中更新 CSRF token，避免写入持久存储。 */
export function setCsrfToken(token: string | null): void {
  csrfToken = token
}

/** 注册全局 401 处理器供路由层跳转登录。 */
export function setUnauthorizedHandler(handler?: () => void): void {
  unauthorizedHandler = handler
}

/** 读取 JSON 错误正文并避免向界面泄露 HTML。 */
async function errorMessage(response: Response): Promise<string> {
  try {
    const data = await response.json() as { detail?: string; message?: string }
    return data.detail || data.message || `请求失败（${response.status}）`
  } catch {
    return `请求失败（${response.status}）`
  }
}

/** 向同源 /api/v1 发请求，默认 12 秒超时并对敏感响应禁用 HTTP 缓存。 */
export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
  timeoutMs = 12_000,
  onUnauthorized?: () => void,
): Promise<T> {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs)
  const method = (options.method || 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (MUTATING_METHODS.has(method) && csrfToken) headers.set('X-CSRF-Token', csrfToken)

  try {
    const headerRecord = Object.fromEntries(headers.entries())
    // 保留常用安全 header 的规范拼写，便于代理审计且 HTTP 语义仍不区分大小写。
    if (headerRecord['x-csrf-token']) {
      headerRecord['X-CSRF-Token'] = headerRecord['x-csrf-token']
      delete headerRecord['x-csrf-token']
    }
    const response = await fetch(`${API_PREFIX}${path}`, {
      ...options,
      method,
      headers: headerRecord,
      credentials: 'include',
      cache: 'no-store',
      signal: controller.signal,
    })
    if (response.status === 401) {
      ;(onUnauthorized || unauthorizedHandler)?.()
      throw new ApiError('登录已失效，请重新登录', 401, 'UNAUTHORIZED')
    }
    if (!response.ok) throw new ApiError(await errorMessage(response), response.status, 'HTTP_ERROR')
    if (response.status === 204) return undefined as T
    return await response.json() as T
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError('服务器响应超时，请检查网络后重试', 0, 'TIMEOUT')
    }
    throw new ApiError(error instanceof Error ? error.message : '网络不可用', 0, 'NETWORK_ERROR')
  } finally {
    window.clearTimeout(timeout)
  }
}
