import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeAll, describe, expect, it, vi } from 'vitest'
import { ConfirmDialog } from './Dialog'

beforeAll(() => {
  // jsdom does not implement the modal dialog API.
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
})

describe('ConfirmDialog', () => {
  it('cancels on Escape, and confirms only through the confirm button', async () => {
    const onCancel = vi.fn()
    const onConfirm = vi.fn()
    render(
      <ConfirmDialog open title="Delete HD-1?" confirmLabel="Delete permanently" tone="danger" onCancel={onCancel} onConfirm={onConfirm}>
        This can't be undone.
      </ConfirmDialog>,
    )

    const dialog = screen.getByRole('dialog', { hidden: true })
    expect(dialog).toHaveAccessibleName('Delete HD-1?')

    fireEvent.keyDown(screen.getByRole('button', { name: 'Delete permanently', hidden: true }), { key: 'Escape' })
    expect(onCancel).toHaveBeenCalledOnce()
    expect(onConfirm).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Delete permanently', hidden: true }))
    expect(onConfirm).toHaveBeenCalledOnce()
  })

  it('disables both buttons while the action runs', () => {
    render(
      <ConfirmDialog open title="Close?" confirmLabel="Close ticket" busy onCancel={() => {}} onConfirm={() => {}}>
        Sure?
      </ConfirmDialog>,
    )
    expect(screen.getByRole('button', { name: 'Working…', hidden: true })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel', hidden: true })).toBeDisabled()
  })
})
