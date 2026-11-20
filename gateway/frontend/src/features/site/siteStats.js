/**
 * 센서 단계 개수 계산 (화면설계안 v-rev.js 의 tot() 와 같은 규칙)
 * - 개수 모양: { NORMAL, WARN, ALARM } (API 명세서 sensorLevels 와 같음)
 */
import { LEVEL } from '@/types/codes'

/** 빈 개수 */
const zero = () => ({ [LEVEL.NORMAL]: 0, [LEVEL.WARN]: 0, [LEVEL.ALARM]: 0 })

/** 개수 여러 개 더하기 */
export function sumLevels(list) {
  return list.reduce((sum, c) => {
    sum[LEVEL.NORMAL] += c[LEVEL.NORMAL] ?? 0
    sum[LEVEL.WARN] += c[LEVEL.WARN] ?? 0
    sum[LEVEL.ALARM] += c[LEVEL.ALARM] ?? 0
    return sum
  }, zero())
}

/** 공장의 전체 센서 개수 = 센서 종류별(전류·온도·CO) 개수의 합 */
export function factoryLevels(factory) {
  return sumLevels(Object.values(factory.levelsByType ?? {}))
}

/** 개수 합계 */
export function totalOf(counts) {
  return counts[LEVEL.NORMAL] + counts[LEVEL.WARN] + counts[LEVEL.ALARM]
}
