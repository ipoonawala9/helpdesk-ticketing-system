import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import { ApiError } from './api/client'
import { router } from './App'
import { AuthProvider } from './auth/AuthContext'
import { Toasts } from './components/Feedback'
import { notify } from './lib/toast'
import './styles/app.css'

/**
 * Failures every screen handles the same way. A 401 is handled by the API
 * client, which ends the session; a 403 means the backend refused something
 * the interface allowed, so it is reported wherever it happens.
 */
function reportGlobally(error: unknown) {
  if (error instanceof ApiError && error.status === 403) {
    notify(error.message || "You don't have permission to do that.", 'error')
  }
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: reportGlobally }),
  mutationCache: new MutationCache({ onError: reportGlobally }),
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
      refetchOnWindowFocus: true,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
        <Toasts />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
