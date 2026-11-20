/**
 * 실시간 값 흉내 (예시 데이터 모드에서만)
 * - 2초마다 센서 값이 받은 값 근처에서 조금씩 흔들림 (설계안 vEquip() every(2000) 와 같은 크기)
 *   전류 ±0.12 A, 온도 ±0.8 ℃, CO ±0.4 ppm — 단계(정상·주의)는 바꾸지 않음
 * - 실제 서버에 붙이면 SSE sensor.value 로 받은 값을 넣을 자리 (TODO: SSE 연결 작업 때)
 * - 화면을 떠나면 타이머 정리
 *
 * 사용: const { values, elapsedSec } = useLiveValues(() => equipment.value?.sensors)
 */
import { onBeforeUnmount, ref } from 'vue'
import { USE_MOCK } from '@/api/mock'
import { sensorTypeOf } from '@/types/codes'

const TICK_MS = 2000
const SPREAD = { CURRENT: 0.24, TEMP: 1.6, CO: 0.8 }

export function useLiveValues(getSensors) {
  /** { 센서 id: 지금 값 } — 비어 있으면 화면은 받은 값을 그대로 씀 */
  const values = ref({})
  /** 화면을 연 뒤 지난 초 (열화상 수신 시각 표시용) */
  const elapsedSec = ref(0)

  let timer = null
  if (USE_MOCK) {
    timer = setInterval(() => {
      elapsedSec.value += TICK_MS / 1000
      const next = {}
      for (const s of getSensors() ?? []) {
        const dec = sensorTypeOf(s.type)?.dec ?? 0
        const noise = (Math.random() - 0.5) * (SPREAD[s.type] ?? 0)
        next[s.id] = Number(Math.max(0, s.value + noise).toFixed(dec))
      }
      values.value = next
    }, TICK_MS)
  }

  onBeforeUnmount(() => clearInterval(timer))

  return { values, elapsedSec }
}
