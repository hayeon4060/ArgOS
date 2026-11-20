/**
 * 점검 관리(수기 서류 OCR) 예시 데이터 (화면설계안 v-inspect.js vInspect(), v-rev3.js inspectList(), API 명세서 E1)
 *
 * - 백엔드에 점검·서류·OCR 컨트롤러가 아직 없어 명세서 E1 의 주소·응답 모양을 따름
 * - 실제 글자 인식은 백엔드가 네이버 CLOVA OCR(템플릿)을 부르고, 키도 백엔드에만 있음 → 프론트는 결과만 받음
 * - 예시 원본은 F01 밀폐공간 작업허가서 한 장뿐 → 올리는 파일은 모두 F01 로 인식한 것으로 만듦 (설계안과 같음)
 * - 상태는 이 파일 안에만 있음 (새로고침하면 처음으로)
 * - 예시 전용: 파일 이름에 '실패' 가 들어가면 인식 실패(FAILED)로 끝남 (실패 화면 확인용)
 */
import f01Scan from '@/assets/images/inspection/f01_filled.jpg'

const pad = (n) => String(n).padStart(2, '0')
const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const iso = (d) => `${ymd(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}:00+09:00`
const daysAgo = (n, h = 9, m = 0) => {
  const d = new Date()
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() - n, h, m)
}
/** 깊은 복사 (화면이 받은 값을 고쳐도 원본이 바뀌지 않게) */
const copy = (v) => JSON.parse(JSON.stringify(v))

/* ───────── 서류 양식 (GET /doc-forms) ───────── */
/**
 * fields: 명세서 { key, label, type, unit, limit } + group(구역) · options(고르는 칸) · row(표의 몇째 줄) (추가 필드)
 * - F01 은 설계안 인식 결과 순서 그대로 (작업 개요 → 가스 농도 측정 → 사전 안전조치 → 작업 종료 확인)
 * - F02 ~ F05 는 ArgOS 시연용 가상 양식의 칸 그대로 (예시 원본 그림은 F01 만 있음)
 */
const PRE_ITEMS = [
  ['작업 전 환기 실시', '배기팬 CF-03 가동'],
  ['산소 · 유해가스 농도 측정 및 기록', ''],
  ['감시인 배치, 작업자와 연락 방법 확인', ''],
  ['송기마스크 · 공기호흡기 등 보호구 비치', ''],
  ['출입금지 표지 게시, 출입 인원 점검', ''],
  ['구조용 장비 비치 (사다리 · 섬유로프 등)', ''],
  ['작업자 밀폐공간 특별안전교육 실시', ''],
  ['작업장 전원 · 밸브 잠금 및 표지 부착', ''],
]

/* ───────── F02 ~ F05 양식 칸 (ArgOS 시연용 가상 양식 — 예시 원본 그림은 없음) ───────── */
const RESULT = ['양호', '불량', '해당 없음']
const f = (key, label, type, group, extra = {}) => ({ key, label, type, group, ...extra })

/** 점검 항목 n개: 판정(양호/불량/해당 없음) + 측정값(단위가 있는 항목만) + 비고 */
function checkItems(items) {
  return items.flatMap(([label, unit], i) => {
    const no = String(i + 1).padStart(2, '0')
    return [
      f(`item${no}`, `${i + 1}. ${label}`, 'choice', 'items', { options: RESULT, row: i + 1 }),
      ...(unit ? [f(`item${no}Value`, `${label} 측정값`, 'number', 'items', { unit, row: i + 1 })] : []),
      f(`item${no}Note`, `${label} 비고`, 'text', 'items', { row: i + 1 }),
    ]
  })
}

/** 이상 사항 및 조치 n줄: 항목 No · 이상 내용 · 조치 내용 · 조치 기한 · 조치 완료일 */
function issueRows(n) {
  return Array.from({ length: n }, (_, i) => i + 1).flatMap((r) => [
    f(`issue${r}No`, `${r}줄 항목 No`, 'number', 'issues', { row: r }),
    f(`issue${r}Problem`, `${r}줄 이상 내용`, 'text', 'issues', { row: r }),
    f(`issue${r}Action`, `${r}줄 조치 내용`, 'text', 'issues', { row: r }),
    f(`issue${r}Due`, `${r}줄 조치 기한`, 'date', 'issues', { row: r }),
    f(`issue${r}DoneAt`, `${r}줄 조치 완료일`, 'date', 'issues', { row: r }),
  ])
}

const F02_FIELDS = [
  f('inspectedAt', '점검 일자·시각', 'datetime', 'info'),
  f('inspType', '점검 구분', 'choice', 'info', { options: ['일상', '주간', '월간'] }),
  f('factory', '공장', 'text', 'info'),
  f('equipmentNo', '설비번호', 'text', 'info', { prefix: 'EF-' }),
  f('zone', '구역', 'text', 'info'),
  f('inspector', '점검자', 'text', 'info'),
  f('checker', '확인자', 'text', 'info'),
  ...checkItems([
    ['배기팬 가동 상태'],
    ['모터 운전 전류', 'A'],
    ['베어링 온도', '℃'],
    ['팬 벨트'],
    ['덕트·후드'],
    ['댐퍼'],
    ['필터 차압', 'Pa'],
    ['배기 풍량', 'm³/min'],
    ['제어반'],
    ['CO 감지기', 'ppm'],
    ['비상 정지 장치'],
    ['예비 팬 전환'],
  ]),
  ...issueRows(3),
  f('verdict', '종합 판정', 'choice', 'verdict', { options: ['정상 운전', '조건부 운전', '운전 중지'] }),
  f('opinion', '종합 의견', 'text', 'verdict'),
]

const F03_FIELDS = [
  f('inspectedAt', '점검 일자·시각', 'datetime', 'info'),
  f('inspType', '점검 구분', 'choice', 'info', { options: ['일상', '월간', '분기'] }),
  f('factory', '공장', 'text', 'info'),
  f('roomName', '전기실명', 'text', 'info'),
  f('equipmentNo', '설비번호', 'text', 'info', { prefix: 'TR-' }),
  f('voltage', '수전 전압', 'choice', 'info', { options: ['22.9kV', '154kV', '기타'] }),
  f('inspector', '점검자', 'text', 'info'),
  f('checker', '확인자', 'text', 'info'),
  ...['R', 'S', 'T'].map((p) => f(`volt${p}`, `${p}상 전압`, 'number', 'measure', { unit: 'V' })),
  ...['R', 'S', 'T'].map((p) => f(`amp${p}`, `${p}상 전류`, 'number', 'measure', { unit: 'A' })),
  f('peakKw', '최대 수요전력', 'number', 'measure', { unit: 'kW' }),
  f('loadRate', '부하율', 'number', 'measure', { unit: '%' }),
  f('powerFactor', '역률', 'number', 'measure', { unit: '%' }),
  f('trTemp', '변압기 온도', 'number', 'measure', { unit: '℃' }),
  f('kwh', '적산 전력량', 'number', 'measure', { unit: 'kWh' }),
  ...checkItems([
    ['변압기 외관'],
    ['차단기(VCB·ACB)'],
    ['표시등·계기'],
    ['보호계전기'],
    ['케이블 접속부(열화상 최고 온도)', '℃'],
    ['절연 상태'],
    ['접지'],
    ['전기실 환경', '℃'],
    ['소화설비·감지기'],
    ['출입 통제'],
  ]),
  ...issueRows(2),
  f('verdict', '종합 판정', 'choice', 'verdict', { options: ['정상', '주의', '운전 정지 필요'] }),
]

const F04_FIELDS = [
  f('eduDate', '교육 일자', 'date', 'edu'),
  f('eduStart', '교육 시작', 'time', 'edu'),
  f('eduEnd', '교육 끝', 'time', 'edu'),
  f('eduHours', '총 시간', 'number', 'edu', { unit: '시간' }),
  f('factory', '공장', 'text', 'edu'),
  f('place', '교육 장소', 'text', 'edu'),
  f('zone', '대상 구역', 'text', 'edu'),
  f('eduType', '교육 구분', 'choice', 'edu', { options: ['정기교육', '채용 시', '작업내용 변경 시', '특별교육', '관리감독자'] }),
  f('specialWork', '특별교육 작업', 'choice', 'edu', { options: ['밀폐공간 작업', '화기 작업', '전기 작업', '유해화학물질 취급', '해당 없음'] }),
  f('method', '교육 방법', 'choice', 'edu', { options: ['집체', '현장', '시청각'] }),
  f('instructorName', '강사 성명', 'text', 'edu'),
  f('instructorOrg', '강사 소속', 'text', 'edu'),
  ...[1, 2, 3].flatMap((r) => [
    f(`subject${r}`, `${r}. 교육 과목`, 'text', 'content', { row: r }),
    f(`detail${r}`, `${r}. 세부 내용`, 'text', 'content', { row: r }),
    f(`minutes${r}`, `${r}. 시간`, 'number', 'content', { unit: '분', row: r }),
  ]),
  f('targetCount', '교육 대상', 'number', 'people', { unit: '명' }),
  f('attendCount', '참석', 'number', 'people', { unit: '명' }),
  f('absentCount', '불참', 'number', 'people', { unit: '명' }),
  ...Array.from({ length: 20 }, (_, i) => i + 1).flatMap((r) => [
    f(`trainee${r}Name`, `${r}. 성명`, 'text', 'roster', { row: r }),
    f(`trainee${r}No`, `${r}. 사번`, 'text', 'roster', { row: r }),
    f(`trainee${r}Sign`, `${r}. 서명`, 'sign', 'roster', { row: r }),
  ]),
  f('absentees', '불참자', 'text', 'people'),
  f('makeupDate', '보충 교육일', 'date', 'people'),
]

// 위험성 평가 및 감소 대책 8줄 (위험성 = 가능성 × 중대성). 양식의 예시 행은 인식하지 않음
const F05_FIELDS = [
  f('evalDate', '평가 일자', 'date', 'info'),
  f('evalType', '평가 구분', 'choice', 'info', { options: ['최초', '정기', '수시', '상시'] }),
  f('factory', '공장', 'text', 'info'),
  f('processWork', '공정·작업', 'text', 'info'),
  f('zone', '구역', 'text', 'info'),
  f('evaluator', '평가자', 'text', 'info'),
  f('participants', '참여 근로자', 'text', 'info'),
  ...Array.from({ length: 8 }, (_, i) => i + 1).flatMap((r) => [
    f(`risk${r}Task`, `${r}. 세부 작업`, 'text', 'risk', { row: r }),
    f(`risk${r}Hazard`, `${r}. 유해·위험요인`, 'text', 'risk', { row: r }),
    f(`risk${r}Accident`, `${r}. 재해 형태`, 'text', 'risk', { row: r }),
    f(`risk${r}Current`, `${r}. 현재 안전조치`, 'text', 'risk', { row: r }),
    f(`risk${r}Likelihood`, `${r}. 가능성`, 'number', 'risk', { min: 1, max: 3, row: r }),
    f(`risk${r}Severity`, `${r}. 중대성`, 'number', 'risk', { min: 1, max: 3, row: r }),
    f(`risk${r}Level`, `${r}. 위험성`, 'number', 'risk', { formula: '가능성 × 중대성', row: r }),
    f(`risk${r}Measure`, `${r}. 감소 대책`, 'text', 'risk', { row: r }),
    f(`risk${r}After`, `${r}. 개선 후 위험성`, 'number', 'risk', { row: r }),
    f(`risk${r}Owner`, `${r}. 담당/완료일`, 'text', 'risk', { row: r }),
  ]),
]

const FORMS = [
  {
    formType: 'F01',
    name: '밀폐공간 작업허가서',
    fields: [
      { key: 'applyDate', label: '신청일', type: 'date', group: 'overview' },
      { key: 'place', label: '작업 장소', type: 'text', group: 'overview' },
      { key: 'equipmentNo', label: '설비번호', type: 'text', group: 'overview' },
      { key: 'workPeriod', label: '작업 기간', type: 'text', group: 'overview' },
      { key: 'manager', label: '작업 책임자', type: 'text', group: 'overview' },
      { key: 'supervisor', label: '감시인', type: 'text', group: 'overview' },
      { key: 'workers', label: '작업자', type: 'text', group: 'overview' },
      { key: 'co0850', label: '08:50 CO', type: 'number', unit: 'ppm', limit: 30, group: 'gas', time: '08:50 작업 전' },
      { key: 'co0930', label: '09:30 CO', type: 'number', unit: 'ppm', limit: 30, group: 'gas', time: '09:30 작업 중' },
      { key: 'co1030', label: '10:30 CO', type: 'number', unit: 'ppm', limit: 30, group: 'gas', time: '10:30 작업 중' },
      ...PRE_ITEMS.map(([label, note], i) => ({ key: `pre${i + 1}`, label, note, type: 'check', group: 'pre' })),
      { key: 'endAt', label: '종료 일시', type: 'datetime', group: 'end' },
      { key: 'exitCheck', label: '작업자 퇴출', type: 'text', group: 'end' },
      { key: 'remark', label: '특이 사항', type: 'text', group: 'end' },
      { key: 'signer', label: '확인자 서명', type: 'text', group: 'end' },
    ],
  },
  { formType: 'F02', name: '환기·배기설비 점검일지', fields: F02_FIELDS },
  { formType: 'F03', name: '수변전설비 점검일지', fields: F03_FIELDS },
  { formType: 'F04', name: '안전보건교육 일지', fields: F04_FIELDS },
  { formType: 'F05', name: '위험성평가표', note: '양식의 예시 행은 인식하지 않음', fields: F05_FIELDS },
]
export const FORM_NAME = Object.fromEntries(FORMS.map((f) => [f.formType, f.name]))

export const docForms = () => copy(FORMS)

/* ───────── F01 예시 인식 결과 ───────── */
// 원본 그림 크기 (설계안 910 × 1286). 상자는 명세서처럼 그림 크기에 대한 비율 [x, y, 너비, 높이]
const W = 910
const H = 1286
const box = (x, y, w, h) => [x / W, y / H, w / W, h / H].map((v) => Math.round(v * 10000) / 10000)

function f01Fields(today) {
  const md = `${today.slice(5, 7)}-${today.slice(8, 10)}`
  return [
    { key: 'applyDate', value: today, box: box(660, 200, 170, 26), needCheck: false },
    { key: 'place', value: '5공장 도장 공정 · 전처리 탈지 탱크 내부', box: box(150, 300, 470, 26), needCheck: false },
    { key: 'equipmentNo', value: 'PT-01', box: box(645, 232, 100, 26), needCheck: false },
    { key: 'workPeriod', value: `${md} 09:00 ~ 12:00`, box: box(150, 332, 580, 26), needCheck: false },
    { key: 'manager', value: '박성훈', box: box(195, 416, 90, 26), needCheck: false },
    { key: 'supervisor', value: '이재호', box: box(195, 450, 90, 26), needCheck: false },
    { key: 'workers', value: '최민규, 정우진', box: box(165, 483, 225, 26), needCheck: true, reason: '글자가 흐립니다' },
    { key: 'co0850', value: '4', box: box(132, 814, 650, 28), needCheck: false },
    { key: 'co0930', value: '6', box: box(132, 851, 650, 28), needCheck: false },
    { key: 'co1030', value: '7', box: box(420, 887, 40, 30), needCheck: true, reason: '숫자 1과 7이 비슷하게 쓰임' },
    ...PRE_ITEMS.map((_, i) => ({ key: `pre${i + 1}`, value: '예', box: null, needCheck: false })),
    { key: 'endAt', value: `${today} 11:50`, box: box(230, 984, 230, 26), needCheck: false },
    { key: 'exitCheck', value: '전원 퇴출 확인', box: box(680, 984, 170, 26), needCheck: false },
    { key: 'remark', value: '이상 없음', box: null, needCheck: false },
    { key: 'signer', value: '박성훈', box: box(158, 1091, 330, 26), needCheck: false },
  ]
}
const labelOf = (formType, key) => FORMS.find((f) => f.formType === formType)?.fields.find((f) => f.key === key)?.label ?? key

/* ───────── 등록된 서류 (GET /inspections) ───────── */
const WHO = ['한지우', '윤서연', '김도현', '박성훈', '이재호']
const PROC = ['도장 공정', '차체 공정', '프레스 공정', '의장 공정']
const fileNameOf = (formType, d, n = 0) => `${FORM_NAME[formType].replace(/[·\s]/g, '')}_${pad(d.getMonth() + 1)}${pad(d.getDate())}${n ? `_${n + 1}` : ''}.pdf`

let nextId = 2300
/** 서류 한 건 (목록 줄 + 인식 결과) */
function makeDoc({ formType, at, status, by, processName, fileName, progress = 100 }) {
  const id = nextId++
  const today = ymd(at)
  const fields = formType === 'F01' ? f01Fields(today) : []
  return {
    id,
    formType,
    formName: FORM_NAME[formType],
    fileName: fileName ?? fileNameOf(formType, at),
    processName: formType === 'F03' ? '변전소' : processName,
    uploadedBy: by,
    uploadedAt: iso(at),
    status, // RECOGNIZING | REVIEW | DONE | FAILED
    progress,
    startedAt: Date.now(),
    ocrMs: 8000, // 인식에 걸리는 시간 (예시)
    fields,
    reviewedBy: status === 'DONE' ? by : null,
    reviewedAt: status === 'DONE' ? iso(new Date(at.getTime() + 25 * 60000)) : null,
  }
}

/** 처음 목록: 오늘 3건(검수 대기 F01 · 인식 중 F03 · 검수 대기 F02) + 지난 12일 저장된 서류 (설계안 Q · inspectList ALL) */
function seed() {
  const list = []
  list.push(makeDoc({ formType: 'F01', at: daysAgo(0, 8, 41), status: 'REVIEW', by: '김도현', processName: '도장 공정' }))
  const f03 = makeDoc({ formType: 'F03', at: daysAgo(0, 8, 32), status: 'RECOGNIZING', by: '한지우', processName: '변전소', progress: 64 })
  // 설계안처럼 화면을 보는 동안 천천히 진행 (90초, 이미 64%)
  f03.ocrMs = 90000
  f03.startedAt = Date.now() - 0.64 * f03.ocrMs
  list.push(f03)
  list.push(makeDoc({ formType: 'F02', at: daysAgo(0, 8, 15), status: 'REVIEW', by: '이재호', processName: '도장 공정' }))
  for (let back = 1; back <= 12; back += 1) {
    const n = 1 + ((back * 7) % 3)
    for (let j = 0; j < n; j += 1) {
      const formType = `F0${1 + ((back + j * 2) % 5)}`
      const at = daysAgo(back, 9 + ((back + j * 3) % 8), (back * 13 + j * 17) % 60)
      list.push(makeDoc({ formType, at, status: 'DONE', by: WHO[(back + j) % 5], processName: PROC[(back + j) % 4], fileName: fileNameOf(formType, at, j) }))
    }
  }
  return list
}
const DOCS = seed()

/* ───────── 인식 진행 (시간 기준, 보통 8초) ───────── */
function advance(doc) {
  if (doc.status !== 'RECOGNIZING') return
  const p = Math.min(100, Math.round(((Date.now() - doc.startedAt) / doc.ocrMs) * 100))
  doc.progress = Math.max(doc.progress, p)
  if (doc.progress < 100) return
  // 예시 전용: 파일 이름에 '실패' 가 있으면 인식 실패
  if (doc.fileName.includes('실패')) {
    doc.status = 'FAILED'
    return
  }
  doc.status = 'REVIEW'
}

const rowOf = (d) => ({
  id: d.id,
  formType: d.formType,
  formName: d.formName,
  fileName: d.fileName,
  processName: d.processName,
  uploadedBy: d.uploadedBy,
  uploadedAt: d.uploadedAt,
  status: d.status,
  progress: d.progress,
  // 추가 필드: 목록에서 '확인 필요 n개' 를 보여 주려고
  needCheckCount: d.fields.filter((f) => f.needCheck).length,
  reviewedAt: d.reviewedAt,
})

/** GET /inspections?from=&to=&formType=&status=&q=&page=&size= */
export function list({ from, to, formType, status, q, page = 0, size = 20 } = {}) {
  DOCS.forEach(advance)
  const day = (s) => s.slice(0, 10)
  const rows = DOCS.filter(
    (d) =>
      (!from || day(d.uploadedAt) >= from) &&
      (!to || day(d.uploadedAt) <= to) &&
      (!formType || d.formType === formType) &&
      (!status || status.split(',').includes(d.status)) &&
      (!q || d.fileName.includes(q) || (d.processName ?? '').includes(q)),
  ).sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt) || b.id - a.id)
  return { items: rows.slice(page * size, page * size + size).map(rowOf), page, size, total: rows.length }
}

/** POST /inspections (파일 → 202 { id, fileName, status }) */
export function upload({ fileName, formType, processName = '도장 공정', by }) {
  const doc = makeDoc({ formType: formType || 'F01', at: new Date(), status: 'RECOGNIZING', by, processName, fileName, progress: 0 })
  // 예시 원본이 F01 뿐이라 양식을 정하지 않으면 F01 로 인식
  if (doc.formType !== 'F01') {
    doc.formType = 'F01'
    doc.formName = FORM_NAME.F01
  }
  doc.fields = f01Fields(ymd(new Date()))
  doc.startedAt = Date.now()
  DOCS.unshift(doc)
  return { id: doc.id, fileName: doc.fileName, status: doc.status }
}

const find = (id) => DOCS.find((d) => d.id === Number(id))

/** GET /inspections/{id}/ocr */
export function ocr(id) {
  const doc = find(id)
  if (!doc) return { error: { code: 'NOT_FOUND', message: '없는 서류입니다.', status: 404 } }
  advance(doc)
  if (doc.status === 'FAILED') return { error: { code: 'OCR_FAILED', message: '글자를 읽지 못했습니다. 더 선명하게 스캔해 다시 올리세요.', status: 422 } }
  const done = doc.status === 'REVIEW' || doc.status === 'DONE'
  const fields = done ? doc.fields.map((f) => ({ ...f, label: labelOf(doc.formType, f.key) })) : []
  return {
    data: {
      id: doc.id,
      status: doc.status,
      progress: doc.progress,
      formType: doc.formType,
      pageCount: 1,
      fileName: doc.fileName,
      fields: copy(fields),
      needCheckCount: fields.filter((f) => f.needCheck).length,
      // 추가 필드: 저장한 사람·시각 (저장된 서류를 열 때 '저장했습니다 · 11-12 16:44' 표시)
      reviewedBy: doc.reviewedBy,
      reviewedAt: doc.reviewedAt,
    },
  }
}

/** GET /inspections/{id}/file → 원본 그림 주소 (예시는 F01 그림 하나) */
/** 예시 모드 전용: 서류 한 장의 이름 · 양식 · 인식 값 (검수 완료 뒤 점검 건에 잇는 데 씀) */
export function docInfo(id) {
  const d = find(id)
  return d ? { inspectionId: d.id, fileName: d.fileName, formType: d.formType, fields: d.fields.map((f) => ({ key: f.key, value: f.value })) } : null
}

export function fileUrl(id) {
  return find(id)?.formType === 'F01' ? f01Scan : null
}

/**
 * PUT /inspections/{id} { status: REVIEW | DONE, fields: [{ key, value, checked }] }
 * - DONE 인데 확인하지 않은 칸이 남으면 409 NEED_CHECK_REMAINS
 */
export function save(id, { status, fields }, by) {
  const doc = find(id)
  if (!doc) return { error: { code: 'NOT_FOUND', message: '없는 서류입니다.', status: 404 } }
  const byKey = Object.fromEntries(fields.map((f) => [f.key, f]))
  const next = doc.fields.map((f) => {
    const sent = byKey[f.key]
    if (!sent) return f
    const changed = sent.value !== f.value
    // 값을 고치거나 '확인'을 누르면 확인 필요가 풀림
    return { ...f, value: sent.value, needCheck: f.needCheck && !(sent.checked || changed) }
  })
  if (status === 'DONE' && next.some((f) => f.needCheck)) {
    return { error: { code: 'NEED_CHECK_REMAINS', message: '확인 필요 칸이 남아 있어 저장할 수 없습니다.', status: 409 } }
  }
  doc.fields = next
  doc.status = status
  if (status === 'DONE') {
    doc.reviewedBy = by
    doc.reviewedAt = iso(new Date())
    // 이은 점검 건을 완료 · 수기로 바꾸는 일은 api/inspections.js 가 inspectionSchedule.linkScan 으로 이어서 함
  }
  return { data: { id: doc.id, status: doc.status, reviewedBy: doc.reviewedBy, reviewedAt: doc.reviewedAt } }
}

/** POST /inspections/{id}/ocr (다시 인식: 고친 값을 지우고 처음부터) */
export function reocr(id) {
  const doc = find(id)
  if (!doc) return { error: { code: 'NOT_FOUND', message: '없는 서류입니다.', status: 404 } }
  doc.status = 'RECOGNIZING'
  doc.progress = 0
  doc.startedAt = Date.now()
  doc.ocrMs = 8000
  doc.fields = f01Fields(doc.uploadedAt.slice(0, 10))
  return { data: { id: doc.id, status: doc.status } }
}
