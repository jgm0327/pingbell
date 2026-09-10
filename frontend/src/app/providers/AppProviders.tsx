import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import { AuthProvider } from '../../features/auth/AuthContext'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      // Default 'online' networkMode pauses queries (fetchStatus: 'paused', stuck isPending)
      // whenever TanStack Query's onlineManager believes the browser is offline. That signal is
      // event-based (window 'online'/'offline') and can desync from the real navigator.onLine
      // state, permanently stalling a query with no error/loading feedback. This is a single-origin
      // app talking to its own backend - a genuinely offline request just fails normally and is
      // handled like any other network error, so there's no reason to pre-emptively pause it.
      networkMode: 'always',
    },
  },
})

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>{children}</AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
