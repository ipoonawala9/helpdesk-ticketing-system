import type { ReactNode } from 'react'
import { ApiError } from '../api/client'
import { useToasts } from '../lib/toast'

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <span className="row" role="status">
      <span className="spinner" aria-hidden="true" />
      <span className="sr-only">{label}</span>
    </span>
  )
}

export function LoadingState({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <div className="row">
        <span className="spinner" aria-hidden="true" />
        <span className="muted">{label}…</span>
      </div>
    </div>
  )
}

export function SkeletonRows({ rows = 5 }: { rows?: number }) {
  return (
    <div className="stack" style={{ padding: 'var(--space-5)' }} aria-hidden="true">
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="skeleton" style={{ height: 18, width: `${92 - index * 7}%` }} />
      ))}
    </div>
  )
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return 'Something went wrong. Try again.'
}

export function ErrorState({ error, onRetry, title = "This couldn't be loaded" }: {
  error: unknown
  onRetry?: () => void
  title?: string
}) {
  return (
    <div className="state" role="alert">
      <h2>{title}</h2>
      <p>{errorMessage(error)}</p>
      {onRetry && (
        <button type="button" className="button button-secondary" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="state">
      <h2>{title}</h2>
      {children && <p>{children}</p>}
      {action}
    </div>
  )
}

export function Notice({ tone = 'info', children }: { tone?: 'info' | 'error' | 'success'; children: ReactNode }) {
  return (
    <div className={`notice ${tone === 'info' ? '' : `notice-${tone}`}`} role={tone === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  )
}

export function Toasts() {
  const toasts = useToasts()
  return (
    <div className="toasts" aria-live="polite" aria-atomic="false">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast ${toast.tone === 'error' ? 'toast-error' : ''}`} role="status">
          {toast.message}
        </div>
      ))}
    </div>
  )
}
