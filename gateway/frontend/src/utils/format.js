/**
 * 날짜·숫자 표시 형식
 */
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
const pad = (n) => String(n).padStart(2, '0')

/** 남은 시간: 46800000ms → '13:00:00' (시:분:초) */
export function formatDuration(ms) {
  const total = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return `${pad(h)}:${pad(m)}:${pad(s)}`
}

/**
 * 날짜만: Date → '2026-11-16' (이 PC 의 시간대 = 한국 시각 기준)
 * - toISOString().slice(0, 10) 은 UTC 라서 오전 9시 전에는 어제 날짜가 되므로 쓰지 않음
 */
export function formatYmd(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 상단 바 시계: 2026-11-16 (월) 15:30:05 */
export function formatClock(date) {
  const ymd = formatYmd(date)
  const hms = `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  return `${ymd} (${WEEKDAYS[date.getDay()]}) ${hms}`
}
