import type { Page } from '../api/types'

export function Pagination<T>({ page, onPage, noun = 'items' }: { page: Page<T>; onPage: (page: number) => void; noun?: string }) {
  if (page.totalElements === 0) return null
  const from = page.page * page.size + 1
  const to = from + page.content.length - 1
  return (
    <nav className="pagination" aria-label="Pagination">
      <span className="muted">
        <span className="mono">{from}–{to}</span> of <span className="mono">{page.totalElements}</span> {noun}
      </span>
      <div className="row">
        <button type="button" className="button button-secondary button-small" onClick={() => onPage(page.page - 1)} disabled={page.first}>
          Previous
        </button>
        <span className="muted mono" aria-live="polite">
          {page.page + 1} / {Math.max(page.totalPages, 1)}
        </span>
        <button type="button" className="button button-secondary button-small" onClick={() => onPage(page.page + 1)} disabled={page.last}>
          Next
        </button>
      </div>
    </nav>
  )
}
