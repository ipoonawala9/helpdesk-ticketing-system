import { useEffect, useId, useRef, useState, type ReactNode } from 'react'

/**
 * Point the dialog's opening animation at the control that opened it, as a
 * position inside the dialog's own box. With nothing to measure it keeps the
 * centre, which is what the stylesheet falls back to.
 */
function anchorToTrigger(dialog: HTMLDialogElement, trigger: Element | null) {
  const from = trigger instanceof HTMLElement ? trigger.getBoundingClientRect() : null
  if (!from || from.width === 0) {
    dialog.style.removeProperty('--dialog-origin')
    return
  }
  const box = dialog.getBoundingClientRect()
  const x = Math.round(from.left + from.width / 2 - box.left)
  const y = Math.round(from.top + from.height / 2 - box.top)
  dialog.style.setProperty('--dialog-origin', `${x}px ${y}px`)
}

/**
 * A modal built on the native dialog element, which provides focus
 * containment, Escape to close and an inert background.
 *
 * It grows out of its trigger and shrinks back into it, so the content stays
 * mounted until the closing animation has run.
 */
export function Dialog({ open, onClose, title, children }: {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  // Content outlives `open` so the closing animation has something to animate.
  const [visible, setVisible] = useState(open)
  if (open && !visible) setVisible(true)
  const titleId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return

    if (open) {
      // Catching a dialog mid-close returns it to open rather than queueing.
      dialog.removeAttribute('data-closing')
      if (!dialog.open) {
        const trigger = document.activeElement
        dialog.showModal()
        anchorToTrigger(dialog, trigger)
      }
      return
    }

    if (!dialog.open) {
      setVisible(false)
      return
    }

    dialog.setAttribute('data-closing', 'true')
    let done = false
    let timer = 0
    const finish = () => {
      if (done) return
      done = true
      window.clearTimeout(timer)
      dialog.removeAttribute('data-closing')
      if (dialog.open) dialog.close()
      setVisible(false)
    }
    // Reading the animations settles the pending style first, so the closing
    // animation exists by the time it is read. Where there is none — reduced
    // motion, or a test environment without the API — close straight away.
    const running = dialog.getAnimations?.() ?? []
    if (running.length === 0) {
      finish()
    } else {
      timer = window.setTimeout(finish, 500)
      void Promise.allSettled(running.map((animation) => animation.finished)).then(finish)
    }
    return () => {
      done = true
      window.clearTimeout(timer)
    }
  }, [open])

  return (
    <dialog
      ref={ref}
      className="dialog"
      aria-labelledby={titleId}
      onClose={onClose}
      onCancel={onClose}
      // Browsers close a modal dialog on Escape themselves, but not every input
      // path raises that close request, so Escape is also handled directly.
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.preventDefault()
          onClose()
        }
      }}
    >
      {visible && (
        <div className="dialog-body">
          <h2 id={titleId}>{title}</h2>
          {children}
        </div>
      )}
    </dialog>
  )
}

export function ConfirmDialog({ open, title, children, confirmLabel, tone = 'default', busy, onConfirm, onCancel }: {
  open: boolean
  title: string
  children: ReactNode
  confirmLabel: string
  tone?: 'default' | 'danger'
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  return (
    <Dialog open={open} onClose={onCancel} title={title}>
      <p>{children}</p>
      <div className="form-actions">
        <button type="button" className="button button-secondary" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
        <button
          type="button"
          className={`button ${tone === 'danger' ? 'button-danger' : ''}`}
          onClick={onConfirm}
          disabled={busy}
          autoFocus
        >
          {busy ? 'Working…' : confirmLabel}
        </button>
      </div>
    </Dialog>
  )
}
