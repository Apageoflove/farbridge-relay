// 为组件测试安装可访问性断言并清理每个用例的 DOM。
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/vue'
import { afterEach, vi } from 'vitest'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})
