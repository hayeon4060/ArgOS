/**
 * 사용자 관리 공통 (화면설계안 v-rev3.js 의 RC · MX, 화면설계서 G1 '권한별로 할 수 있는 범위')
 */
import { ROLE, ROLE_LABEL } from '@/types/codes'

export { ROLE, ROLE_LABEL }

/** 권한 색 (설계안 RC: 본사 진남색 · 공장 파랑 · 공정 청록) */
export const ROLE_COLOR = {
  [ROLE.HQ_ADMIN]: '#0f172a',
  [ROLE.PLANT_ADMIN]: '#2563eb',
  [ROLE.WORKER]: '#0d9488',
}

/** 권한 카드 설명 (사용자 추가·수정의 권한 고르기) */
export const ROLE_DESC = {
  [ROLE.HQ_ADMIN]: '모든 사업장 · PC 웹',
  [ROLE.PLANT_ADMIN]: '담당 공장 전체 · PC 웹',
  [ROLE.WORKER]: '배정된 공정만 · 모바일 앱',
}

/** 권한별 사용 범위 표 (설계안 MX — 읽기 전용). 'ok' 는 체크, 빈칸은 못 함 */
export const MATRIX = [
  ['볼 수 있는 범위', '모든 사업장', '담당 공장', '배정 공정'],
  ['사용자 조회', '전체', '소속 공장 · 공정', '본인만'],
  ['사용자 등록 · 삭제', '전체', '소속 공장 · 공정', ''],
  ['사용자 정보 수정', '전체', '소속 공장 · 공정', '본인만'],
  ['주의 확인 · 셧다운', '', 'ok', ''],
  ['체크리스트 수행', '', '', 'ok'],
  ['설비 기준값 변경', 'ok', 'ok', ''],
  ['공장 관리', 'ok', '담당 공장', ''],
  ['이용 로그 조회', 'ok', '담당 공장', ''],
  ['채팅', 'ok', 'ok', ''],
]

/** 이 사람이 줄 수 있는 권한 (본사 관리자 권한은 본사 관리자만) */
export const rolesFor = (actorRole) =>
  actorRole === ROLE.HQ_ADMIN ? [ROLE.HQ_ADMIN, ROLE.PLANT_ADMIN, ROLE.WORKER] : [ROLE.PLANT_ADMIN, ROLE.WORKER]

/** 이 사용자를 고칠·지울 수 있는가 (공장 담당자는 본사 관리자를 다룰 수 없음) */
export const canManage = (actor, user) => !!user && (actor.role === ROLE.HQ_ADMIN || user.role !== ROLE.HQ_ADMIN)

/** '2026-11-12T16:10:41+09:00' → '11-12 16:10' */
export const shortTime = (iso) => (iso ? `${iso.slice(5, 10)} ${iso.slice(11, 16)}` : '–')
/** '2026-10-05T09:00:00+09:00' → '2026-10-05' */
export const dateOf = (iso) => (iso ? iso.slice(0, 10) : '–')
