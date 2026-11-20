/**
 * 화면별 뼈대 배치 (PageSkeleton 이 이 값으로 회색 칸을 그림)
 *
 * - 뼈대 모양은 2종 (라우트 meta.skeleton)
 *   · 'dashboard': 왼쪽 큰 칸 + 오른쪽 카드들 (사업장 선택 · 사업장 현황 · 공장 대시보드 · 공정 배치도 · 설비 상세)
 *   · 'table'    : 조건 줄 + 숫자 칸·표·차트 (통계·이력 · 점검 · 사용자 · 공장 관리 · 이용 로그)
 * - 같은 모양 안에서도 화면마다 카드 칸 수·높이가 달라서, 데이터가 왔을 때 자리가 튀지 않도록
 *   화면(라우트 이름)별 배치를 따로 둠. 값은 1920×1080 실제 화면에서 잰 카드 위치·높이 (2026-11-20)
 * - 화면 이름에 맞는 배치가 없으면 모양별 기본 배치(DEFAULT_LAYOUT)
 *
 * 배치 값
 *  columns: CSS grid-template-columns (실제 화면과 같은 값)
 *  rows: 위에서 아래로 한 줄씩 { size: 줄 높이, areas: 칸 이름(grid-template-areas 한 줄), bar: 조건·탭 줄이면 true }
 *   · 같은 이름이 여러 칸에 있으면 하나의 큰 칸으로 합쳐짐 (예: 'map' 이 세 줄 → 왼쪽 큰 칸)
 *   · 칸 이름 'tab' : 탭 · 경로 줄 → 왼쪽·오른쪽 작은 칸 2개
 *   · 칸 이름 'kpi' : 숫자 칸 4개
 *   · 그 밖 이름   : 회색 칸 하나 (카드 한 장 자리)
 *  trim: 실제 화면이 본문 높이보다 짧으면 그만큼 뺌 (없으면 본문 높이를 꽉 채움)
 */

/** 대시보드 위 줄 공통 열: 왼쪽 넓은 열 + 오른쪽 628px (사업장 현황 · 공장 대시보드 · 공정 배치도) */
const DASH_COLUMNS = 'minmax(0, 1fr) 628px'

/** 사업장 현황 · 공장 대시보드: 지도 | 시간대별 알림 · 오늘 알림 내역, 아래 전체 폭 상태 카드 */
const OVERVIEW = {
  columns: DASH_COLUMNS,
  rows: [
    { size: '274px', areas: 'map hourly' },
    { size: '1fr', areas: 'map alarms' },
    { size: '264px', areas: 'wide wide' },
  ],
}

export const SKELETON_LAYOUTS = {
  /* ───────── dashboard 모양 ───────── */

  // 사업장 선택: 왼쪽 700px (지도 · 센서 단계) | 오른쪽 사업장 목록
  'site-select': {
    columns: 'minmax(0, 700px) minmax(0, 1fr)',
    rows: [
      { size: '600px', areas: 'map list' },
      { size: '1fr', areas: 'sensor list' },
    ],
  },

  'site-dashboard': OVERVIEW,
  dashboard: OVERVIEW, // 공장 대시보드 (라우트 이름이 'dashboard')

  // 공정 배치도: 배치도 | 기준값 근접 · 오늘 알림 내역 · 오늘 점검
  process: {
    columns: DASH_COLUMNS,
    trim: '21px',
    rows: [
      { size: '270px', areas: 'map near' },
      { size: '1fr', areas: 'map alarms' },
      { size: '310px', areas: 'map inspect' },
    ],
  },

  // 설비 상세: 경로 줄 / 설비 위치 · 센서 게이지 · 열화상 / 추이(두 칸) · 이벤트
  equipment: {
    columns: 'minmax(0, 560fr) minmax(0, 820fr) minmax(0, 468fr)',
    rows: [
      { size: '40px', areas: 'tab tab tab', bar: true },
      { size: '430px', areas: 'location gauges thermal' },
      { size: '1fr', areas: 'trend trend events' },
    ],
  },

  /* ───────── table 모양 ───────── */

  // 통계·이력: 조건 줄 / 숫자 칸 4개 / 알림 이력 표 | 일별 추이 · 공정별 알림
  history: {
    columns: 'minmax(0, 1180fr) minmax(0, 684fr)',
    rows: [
      { size: '60px', areas: 'filter filter', bar: true },
      { size: '116px', areas: 'kpi kpi' },
      { size: '1fr', areas: 'table trend' },
      { size: '1fr', areas: 'table byProcess' },
    ],
  },

  // 점검 관리: 서류 양식 줄 / 달력 · 서류 등록 | 점검 이력
  inspection: {
    columns: 'minmax(0, 900px) minmax(0, 1fr)',
    rows: [
      { size: '48px', areas: 'template template', bar: true },
      { size: '1fr', areas: 'calendar history' },
      { size: '168px', areas: 'upload history' },
    ],
  },

  // 사용자 관리: 탭 줄 / 권한 요약 · 권한 표 | 사용자 목록 | 상세 패널
  user: {
    columns: 'minmax(0, 380fr) minmax(0, 1020fr) minmax(0, 448fr)',
    trim: '16px',
    rows: [
      { size: '38px', areas: 'tab tab tab', bar: true },
      { size: '282px', areas: 'summary list panel' },
      { size: '1fr', areas: 'matrix list panel' },
    ],
  },

  // 공장 관리: 탭 줄 / 설비 목록 | 배치도 | 설비 정보
  factory: {
    columns: 'minmax(0, 300fr) minmax(0, 1128fr) minmax(0, 420fr)',
    trim: '16px',
    rows: [
      { size: '38px', areas: 'tab tab tab', bar: true },
      { size: '1fr', areas: 'list map panel' },
    ],
  },

  // 이용 로그: 탭 줄 / 조건 줄 / 로그 표 | 유형별 · 시간대별
  logs: {
    columns: 'minmax(0, 1420fr) minmax(0, 444fr)',
    rows: [
      { size: '31px', areas: 'tab tab', bar: true },
      { size: '60px', areas: 'filter filter', bar: true },
      { size: '346px', areas: 'table types' },
      { size: '1fr', areas: 'table hours' },
    ],
  },
}

/** 화면 이름에 맞는 배치가 없을 때 모양별 기본 배치 */
export const DEFAULT_LAYOUT = {
  dashboard: SKELETON_LAYOUTS.process,
  table: SKELETON_LAYOUTS.history,
}

/** 모양 · 화면 이름 → 배치 */
export function layoutOf(shape, name) {
  return SKELETON_LAYOUTS[name] ?? DEFAULT_LAYOUT[shape] ?? DEFAULT_LAYOUT.dashboard
}
