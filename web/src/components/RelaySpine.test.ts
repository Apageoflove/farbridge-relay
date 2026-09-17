// 验证链路脊柱只展示用户可理解的昵称，不泄露真实设备标识。
import { render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import RelaySpine from './RelaySpine.vue'

describe('RelaySpine', () => {
  it('默认展示安卓到 iPhone，不再显示 OnePlus 或服务端节点文案', () => {
    render(RelaySpine)
    expect(screen.getByText('安卓')).toBeInTheDocument()
    expect(screen.getByText('iPhone')).toBeInTheDocument()
    expect(screen.queryByText('OnePlus')).not.toBeInTheDocument()
    expect(screen.queryByText('Server')).not.toBeInTheDocument()
  })

  it('紧凑链路的两端文字和箭头共用明确的对齐语义', () => {
    render(RelaySpine, { props: { compact: true } })
    expect(screen.getByTestId('relay-source')).toHaveClass('relay-node')
    expect(screen.getByTestId('relay-arrow')).toHaveClass('relay-arrow')
    expect(screen.getByTestId('relay-target')).toHaveClass('relay-node')
    expect(screen.getByTestId('relay-arrow')).toHaveTextContent('→')
  })
})
