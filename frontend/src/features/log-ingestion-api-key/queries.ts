import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as apiKeyApi from './api'

const apiKeysKey = (monitorId: number) => ['monitors', monitorId, 'api-keys'] as const

export function useLogIngestionApiKeys(monitorId: number) {
  return useQuery({ queryKey: apiKeysKey(monitorId), queryFn: () => apiKeyApi.getApiKeys(monitorId) })
}

export function useIssueLogIngestionApiKey(monitorId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiKeyApi.issueApiKey(monitorId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: apiKeysKey(monitorId) }),
  })
}

export function useRevokeLogIngestionApiKey(monitorId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (keyId: number) => apiKeyApi.revokeApiKey(monitorId, keyId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: apiKeysKey(monitorId) }),
  })
}
