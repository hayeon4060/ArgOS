/**
 * 담당자 앱 코드 값 (웹 공통 코드는 '@/types/codes')
 */

/** 체크리스트 진행 상태 (checklist.status) — 표시 이름 */
export const CHECKLIST_STATUS_LABEL = Object.freeze({
  SENT: '새로 받음',
  IN_PROGRESS: '진행 중',
  REPORTED: '보고함 · 확인 대기',
  CONFIRMED: '확인 완료',
  CANCELLED: '취소됨',
})

/** 항목 점검 결과 (명세서 M2 PATCH /checklists/{id}/items/{itemId} 의 result) — 첫 값이 기본 */
export const ITEM_RESULTS = Object.freeze(['이상 없음', '조치함', '부품 교체 예정', '추가 점검 필요'])
