import type { StatusTone } from '../../shared/components/StatusBadge'
import type { Confidence } from './types'

const CONFIDENCE_META: Record<Confidence, { label: string; tone: StatusTone }> = {
  HIGH: { label: '확신도 높음', tone: 'danger' },
  MEDIUM: { label: '확신도 중간', tone: 'warning' },
  LOW: { label: '확신도 낮음', tone: 'neutral' },
}

export function confidenceMeta(confidence: Confidence): { label: string; tone: StatusTone } {
  return CONFIDENCE_META[confidence]
}
