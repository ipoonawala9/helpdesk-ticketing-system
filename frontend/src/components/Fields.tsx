import { useId, useState, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'

interface FieldProps {
  label: string
  hint?: ReactNode
  error?: string
  className?: string
}

// While a field shows an error, the error replaces its hint.
function describedBy(id: string, hint?: ReactNode, error?: string) {
  return [hint && !error ? `${id}-hint` : '', error ? `${id}-error` : ''].filter(Boolean).join(' ') || undefined
}

export function TextField({ label, hint, error, className, ...input }: FieldProps & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId()
  return (
    <div className={`field ${className ?? ''}`}>
      <label className="field-label" htmlFor={id}>{label}</label>
      <input id={id} className="input" aria-invalid={error ? true : undefined} aria-describedby={describedBy(id, hint, error)} {...input} />
      {hint && !error && <span id={`${id}-hint`} className="field-hint">{hint}</span>}
      {error && <span id={`${id}-error`} className="field-error">{error}</span>}
    </div>
  )
}

export function SelectField({ label, hint, error, className, children, ...select }: FieldProps & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId()
  return (
    <div className={`field ${className ?? ''}`}>
      <label className="field-label" htmlFor={id}>{label}</label>
      <select id={id} className="select" aria-invalid={error ? true : undefined} aria-describedby={describedBy(id, hint, error)} {...select}>
        {children}
      </select>
      {hint && !error && <span id={`${id}-hint`} className="field-hint">{hint}</span>}
      {error && <span id={`${id}-error`} className="field-error">{error}</span>}
    </div>
  )
}

export function TextAreaField({ label, hint, error, className, maxLength, value, ...textarea }: FieldProps & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId()
  const length = typeof value === 'string' ? value.length : 0
  return (
    <div className={`field ${className ?? ''}`}>
      <label className="field-label" htmlFor={id}>{label}</label>
      <textarea id={id} className="textarea" maxLength={maxLength} value={value} aria-invalid={error ? true : undefined} aria-describedby={describedBy(id, hint, error)} {...textarea} />
      <div className="row" style={{ justifyContent: 'space-between' }}>
        {hint ? <span id={`${id}-hint`} className="field-hint">{hint}</span> : <span />}
        {maxLength && <span className="counter" aria-hidden="true">{length}/{maxLength}</span>}
      </div>
      {error && <span id={`${id}-error`} className="field-error">{error}</span>}
    </div>
  )
}

/**
 * A password box with a show/hide control, for checking what you typed before
 * submitting. The button states what it will do, and announces the current
 * state to assistive technology.
 */
export function PasswordField({ label, hint, error, className, ...input }: FieldProps & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId()
  const [visible, setVisible] = useState(false)

  return (
    <div className={`field ${className ?? ''}`}>
      <label className="field-label" htmlFor={id}>{label}</label>
      <div className="input-affix">
        <input
          id={id}
          className="input"
          type={visible ? 'text' : 'password'}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy(id, hint, error)}
          {...input}
        />
        <button
          type="button"
          className="input-affix-button"
          onClick={() => setVisible((shown) => !shown)}
          aria-pressed={visible}
          aria-controls={id}
          aria-label={visible ? 'Hide password' : 'Show password'}
          title={visible ? 'Hide password' : 'Show password'}
        >
          <EyeIcon crossed={visible} />
        </button>
      </div>
      {hint && !error && <span id={`${id}-hint`} className="field-hint">{hint}</span>}
      {error && <span id={`${id}-error`} className="field-error">{error}</span>}
    </div>
  )
}

function EyeIcon({ crossed }: { crossed: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
      <path d="M2 12s3.6-6.5 10-6.5S22 12 22 12s-3.6 6.5-10 6.5S2 12 2 12Z" />
      <circle cx="12" cy="12" r="2.6" />
      {crossed && <path d="M4 20 20 4" />}
    </svg>
  )
}
