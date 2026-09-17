// 验证 iPhone 主屏幕 PWA 所需 manifest 元数据。
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('web app manifest', () => {
  it('使用 standalone 模式和安全起始页', () => {
    const manifest = JSON.parse(readFileSync(resolve(process.cwd(), 'public/manifest.webmanifest'), 'utf8')) as Record<string, unknown>
    expect(manifest.name).toBe('远桥')
    expect(manifest.short_name).toBe('远桥')
    expect(manifest.display).toBe('standalone')
    expect(manifest.start_url).toBe('/messages')
    expect(manifest.theme_color).toBe('#163A70')
    expect(manifest.icons).toEqual(expect.arrayContaining([
      expect.objectContaining({ src: '/icons/phone-mirror-192.png', sizes: '192x192' }),
      expect.objectContaining({ src: '/icons/phone-mirror-512.png', sizes: '512x512' }),
    ]))
  })
})
