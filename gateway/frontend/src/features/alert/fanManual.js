/**
 * 가스 주의 창에서 담당자가 직접 정한 배기팬 속도(Hz) 기억 (2026-11-20)
 * - 주의 알림 창 → 주의 상세 창처럼 창이 바뀌어도 같은 값을 보여 주려고 화면 전체가 같이 씀
 * - 열쇠: '배기팬 설비 id | 알림 발생 시각' → 다른 알림이 오면 자연히 다른 값
 * - 새로고침하면 사라짐. 서버가 알림 fanAuto.manualHz 를 실어 주면 그 값을 씀
 *   TODO(백엔드: fanAuto.manualHz — docs/api/fan-speed-control.md 2장 1번)
 */
import { reactive } from 'vue'

const sent = reactive({})

const keyOf = (fan) => `${fan.fanEquipmentId ?? fan.equipmentId}|${fan.at}`

/** 이 알림의 배기팬을 직접 정한 값 (없으면 null) */
export const manualHzOf = (fan) => sent[keyOf(fan)] ?? fan.manualHz ?? null

/** 직접 정한 값을 기억 (FanSpeedControl 이 '적용'에 성공했을 때) */
export function rememberManualHz(fan, hz) {
  sent[keyOf(fan)] = hz
}
