import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { PasswordField } from './Fields'

describe('PasswordField', () => {
  it('hides the password until asked, and says which action the button performs', async () => {
    render(<PasswordField label="Password" defaultValue="hunter2" />)

    const input = screen.getByLabelText('Password')
    const toggle = screen.getByRole('button', { name: 'Show password' })

    expect(input).toHaveAttribute('type', 'password')
    expect(toggle).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(toggle)

    expect(input).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Hide password' })).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(screen.getByRole('button', { name: 'Hide password' }))
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password')
  })

  it('keeps what was typed when toggling, and reports errors instead of hints', async () => {
    const { rerender } = render(<PasswordField label="Password" defaultValue="" hint="At least 8 characters." />)
    const input = screen.getByLabelText('Password')

    await userEvent.type(input, 'a-secret')
    await userEvent.click(screen.getByRole('button', { name: 'Show password' }))
    expect(screen.getByLabelText('Password')).toHaveValue('a-secret')
    expect(screen.getByText('At least 8 characters.')).toBeInTheDocument()

    rerender(<PasswordField label="Password" defaultValue="" hint="At least 8 characters." error="Use at least 8 characters." />)
    expect(screen.getByText('Use at least 8 characters.')).toBeInTheDocument()
    expect(screen.queryByText('At least 8 characters.')).toBeNull()
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true')
  })
})
