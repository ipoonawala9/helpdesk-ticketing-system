import { useEffect, useState } from 'react'

export function useDebounced<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay)
    return () => window.clearTimeout(timer)
  }, [value, delay])
  return debounced
}

export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = title ? `${title} · HelpDesk` : 'HelpDesk'
  }, [title])
}
