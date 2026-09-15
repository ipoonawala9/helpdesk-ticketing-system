import { useSyncExternalStore } from 'react'

export interface Toast {
  id: number
  message: string
  tone: 'info' | 'error'
}

let toasts: Toast[] = []
let nextId = 1
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((listener) => listener())
}

/** Shows a short message. Callable from anywhere, including outside React. */
export function notify(message: string, tone: Toast['tone'] = 'info') {
  const toast = { id: nextId++, message, tone }
  // The same message twice in a row (e.g. two failed requests) is shown once.
  if (toasts.some((existing) => existing.message === message)) return
  toasts = [...toasts, toast]
  emit()
  window.setTimeout(() => {
    toasts = toasts.filter((existing) => existing.id !== toast.id)
    emit()
  }, tone === 'error' ? 6000 : 4000)
}

export function useToasts(): Toast[] {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => toasts,
  )
}
