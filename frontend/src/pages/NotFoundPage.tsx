import { Link } from 'react-router'
import { EmptyState } from '../components/Feedback'
import { useDocumentTitle } from '../lib/hooks'

export function NotFoundPage() {
  useDocumentTitle('Page not found')
  return (
    <div className="page">
      <div className="card">
        <EmptyState title="There's no page here" action={<Link className="button button-secondary" to="/dashboard">Go to your dashboard</Link>}>
          The link may be old, or the address mistyped.
        </EmptyState>
      </div>
    </div>
  )
}
