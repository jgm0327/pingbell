import { useMutation } from '@tanstack/react-query'
import * as logAnalysisApi from './api'

export function useAnalyzeLog(monitorId: number) {
  return useMutation({
    mutationFn: ({ file, question }: { file: File; question: string }) =>
      logAnalysisApi.analyzeLog(monitorId, file, question),
  })
}
