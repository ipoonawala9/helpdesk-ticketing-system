import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'

interface FieldProps {
  label: string
  hint?: ReactNode
  error?: string
  className?: string
}

function describedBy(id: string, hint?: ReactNode, error?: string) {
  return [hint ? `${id}-hint` : '', error ? `${id}-error` : ''].filter(Boolean).join(' ') || undefined
}

export function TextField({ label, hint, error, className, ...input }: FieldProps & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId()
  return (
    <div className={`field ${className ?? ''}`}>
      <label className="field-label" htmlFor={id}>{label}</label>
      <input id={id} className="input" aria-invalid={error ? true : undefined} aria-describedby={describedBy(id, hint, error)} {...input} />
      {hint && <span id={`${id}-hint`} className="field-hint">{hint}</span>}
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
      {hint && <span id={`${id}-hint`} className="field-hint">{hint}</span>}
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
