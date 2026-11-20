/**
 * 사업장 화면 공통 표시 도우미
 */
import { LEVEL_META, SENSOR_STAGES } from '@/types/codes'

/** 센서 단계 3가지(정상·주의·경보)의 표시 정보: 개수 표시·범례에서 같은 순서로 사용 */
export const STAGES = SENSOR_STAGES.map((level) => ({
  level,
  key: LEVEL_META[level].key, // CSS 클래스용 (is-warn 등)
  label: LEVEL_META[level].label,
  color: LEVEL_META[level].color,
}))

/** 상태 → CSS 클래스용 이름 (is-normal, is-warn ...) */
export const keyOf = (level) => LEVEL_META[level]?.key ?? 'normal'

/** 공정 이름은 표에서 짧게: '도장 공정' → '도장' (설계안 표 모양) */
export const shortName = (name) => name.replace(/ 공정$/, '')
