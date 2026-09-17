// 验证登录表单、错误提示和可访问状态播报。
import { fireEvent, render, screen } from '@testing-library/vue'
import { describe, expect, it, vi } from 'vitest'
import LoginView from './LoginView.vue'

describe('LoginView', () => {
  it('密码眼睛切换显示状态时保留输入值和焦点字段', async () => {
    render(LoginView, { props: { login: vi.fn() } })
    const password = screen.getByLabelText('密码') as HTMLInputElement
    await fireEvent.update(password, 'temporary-secret')
    expect(password.type).toBe('password')
    await fireEvent.click(screen.getByRole('button', { name: '显示密码' }))
    expect(password.type).toBe('text')
    expect(password.value).toBe('temporary-secret')
    await fireEvent.click(screen.getByRole('button', { name: '隐藏密码' }))
    expect(password.type).toBe('password')
    expect(password.value).toBe('temporary-secret')
  })

  it('提交账号密码并清空密码字段', async () => {
    const login = vi.fn().mockResolvedValue(undefined)
    render(LoginView, { props: { login } })
    await fireEvent.update(screen.getByLabelText('用户名'), 'admin')
    const password = screen.getByLabelText('密码') as HTMLInputElement
    await fireEvent.update(password, 'secret')
    await fireEvent.click(screen.getByRole('button', { name: '登录' }))
    expect(login).toHaveBeenCalledWith('admin', 'secret')
    expect(password.value).toBe('')
  })

  it('登录失败给出可继续操作的错误', async () => {
    render(LoginView, { props: { login: vi.fn().mockRejectedValue(new Error('账号或密码错误')) } })
    await fireEvent.update(screen.getByLabelText('用户名'), 'admin')
    const password = screen.getByLabelText('密码') as HTMLInputElement
    await fireEvent.update(password, 'wrong')
    await fireEvent.click(screen.getByRole('button', { name: '登录' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('账号或密码错误')
    expect(password.value).toBe('wrong')
    await fireEvent.click(screen.getByRole('button', { name: '显示密码' }))
    expect(password.type).toBe('text')
    expect(password.value).toBe('wrong')
    expect(screen.getByRole('button', { name: '重新登录' })).toBeInTheDocument()
  })
})
