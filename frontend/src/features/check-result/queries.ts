import { useQuery } from '@tanstack/react-query'
import * as checkResultApi from './api'

export function useCheckResults(monitorId: number, page: number, size: number) {
  return useQuery({
    queryKey: ['monitors', monitorId, 'checks', { page, size }] as const,
    queryFn: () => checkResultApi.getCheckResults(monitorId, page, size),
  })
}
