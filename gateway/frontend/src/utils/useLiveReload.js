/**
 * 데이터가 바뀌면 화면 데이터를 조용히 다시 불러오기
 * - api/live.js 의 live.revision 이 바뀔 때마다 reload({ silent: true }) 를 부름
 *   (지금: 예시 상황 바꾸기·주의 확인·체크리스트 진행 / 나중: SSE 이벤트)
 * - silent: '불러오는 중' 화면으로 바꾸지 않고 값만 바꿈 → 카드가 다시 올라오지 않고 숫자·색만 바뀜
 *
 * 사용: useLiveReload(load)  — load 는 ({ silent }) 를 받는 함수
 */
import { watch } from 'vue'
import { live } from '@/api/live'

export function useLiveReload(reload) {
  watch(
    () => live.revision,
    () => reload({ silent: true }),
  )
}
