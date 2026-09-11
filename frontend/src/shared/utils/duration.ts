/** Formats the span between two ISO datetime strings as a Korean "n일 n시간 n분" string. Pass
 * `endedAt: null` for an still-open span (e.g. an OPEN Incident), which measures up to now. */
export function formatDuration(startedAt: string, endedAt: string | null): string {
  const start = new Date(startedAt).getTime()
  const end = endedAt ? new Date(endedAt).getTime() : Date.now()
  const totalMinutes = Math.max(0, Math.round((end - start) / 60_000))

  const days = Math.floor(totalMinutes / (60 * 24))
  const hours = Math.floor((totalMinutes % (60 * 24)) / 60)
  const minutes = totalMinutes % 60

  const parts: string[] = []
  if (days > 0) {
    parts.push(`${days}일`)
  }
  if (hours > 0) {
    parts.push(`${hours}시간`)
  }
  if (minutes > 0 || parts.length === 0) {
    parts.push(`${minutes}분`)
  }
  return parts.join(' ')
}
