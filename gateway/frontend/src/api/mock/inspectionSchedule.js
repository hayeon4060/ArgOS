/**
 * 점검 스케줄 · 이력 · 전자 서류 양식 예시 데이터 (2026-11-20 점검 관리 개편안 A안)
 *
 * - 울산 5공장 도장 공정. 날짜는 오늘(serverNow) 기준으로 만듦 → 언제 열어도 이번 달 · 최근 7일에 데이터가 있음
 * - 규칙 (시안과 같음)
 *   · 평일마다 F02 환기·배기설비 점검일지 2건 — 도장 설비 8개를 돌아가며
 *   · 월요일마다 F03 수변전설비 점검일지 1건 — 5공장 변전소
 *   · 오늘은 주말이어도 점검을 넣음 (이력 맨 위 '오늘 점검'이 비지 않게)
 *   · 지난 건 중 몇 건은 안 함(누락), 몇 건은 불량
 * - 상태는 백엔드 InspectionService.scheduleStatus 와 같은 규칙으로 읽을 때마다 계산
 *   안 한 건: 예정 시각이 지났으면 DELAYED(누락), 아니면 PLANNED(예정)
 * - 상태는 이 파일 안에만 있음 (새로고침하면 처음으로)
 */
import { serverNow } from '../clock'

const pad = (n) => String(n).padStart(2, '0')
const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const hm = (d) => `${pad(d.getHours())}:${pad(d.getMinutes())}`

/* ───────── 설비 · 점검자 (GET /inspections/schedule/options) ───────── */
const EQUIPMENTS = [
  ...['전처리 탈지 탱크', '전착 탱크', '전착 건조로', '중도 부스', '중도 건조로', '상도 부스', '상도 건조로', '도료 혼합실'].map((name, i) => ({
    id: 101 + i,
    name,
    code: `EF-${pad(i + 1)}`, // 배기팬 번호 (F02 양식의 '설비번호 EF - __')
    formType: 'F02',
  })),
  { id: 109, name: '5공장 변전소', code: 'TR-01', formType: 'F03' },
]
const INSPECTORS = [
  { id: 21, name: '김도현' },
  { id: 22, name: '박성훈' },
  { id: 23, name: '이재호' },
  { id: 24, name: '정해준' },
]
const eqById = (id) => EQUIPMENTS.find((e) => e.id === Number(id))
const whoById = (id) => INSPECTORS.find((w) => w.id === Number(id))

export const options = () => ({ equipments: EQUIPMENTS, inspectors: INSPECTORS })

/* ───────── 점검 건 만들기 ───────── */
let seq = 700
/** 점검 한 건 (done: 했는지. 상태는 읽을 때 계산) */
function makeItem({ date, time, formType, eq, who, done = false }) {
  seq += 1
  return { scheduleId: seq, date, time, formType, eq, who, done, source: null, result: null, doneAt: null }
}

function seed() {
  const now = serverNow()
  const today = ymd(now)
  const laterToday = now.getHours() >= 21 ? '23:30' : `${pad(now.getHours() + 2)}:00`
  const start = new Date(now.getFullYear(), now.getMonth() - 3, 1) // 석 달 전 1일부터
  const end = new Date(now.getFullYear(), now.getMonth() + 3, 0) // 두 달 뒤 말일까지
  const list = []
  let k = 0 // 점검한 날 순번 (설비 · 시각 · 점검자를 돌아가며 고름)
  for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
    const date = ymd(d)
    const w = d.getDay()
    if ((w === 0 || w === 6) && date !== today) continue
    const rows = [
      ['F02', EQUIPMENTS[k % 8], ['09:10', '09:20', '09:40'][k % 3], INSPECTORS[k % 3]],
      // 오늘 두 번째 건은 지금보다 2시간 뒤(늦어도 23:30)로 → 언제 열어도 '예정'으로 보이게
      ['F02', EQUIPMENTS[(k + 3) % 8], date === today ? laterToday : ['14:05', '15:20', '16:30'][k % 3], INSPECTORS[(k + 1) % 3]],
    ]
    if (w === 1) rows.push(['F03', EQUIPMENTS[8], '09:40', INSPECTORS[3]])
    rows.forEach(([formType, eq, time, who], n) => {
      // 지난 날은 했음 / 오늘은 첫 건만 했음 / 앞날은 안 함
      const done = date < today || (date === today && n === 0)
      const it = makeItem({ date, time, formType, eq, who, done })
      if (done) {
        it.source = (k + n) % 3 === 0 ? 'DIGITAL' : 'PAPER'
        it.result = 'OK'
        it.doneAt = `${date}T${time}:00+09:00`
      }
      list.push(it)
    })
    k += 1
  }

  // 지난 건(최신순) 몇째를 누락 · 불량으로 — 기본 7일 이력에 둘 다 보이게 앞쪽 번호를 넣음
  const past = list.filter((it) => it.date < today).sort((a, b) => (b.date + b.time).localeCompare(a.date + a.time))
  ;[2, 9, 23, 41, 60, 85].forEach((i) => past[i] && Object.assign(past[i], { done: false, source: null, result: null, doneAt: null }))
  ;[1, 5, 14, 30, 47, 70].forEach((i) => past[i]?.done && (past[i].result = 'NG'))
  return list
}

const ITEMS = seed()
/** 저장한 서류 양식 값 (scheduleId → values). 없으면 sampleValues 로 만듦 */
const VALUES = {}

/** 상태 (백엔드 scheduleStatus 와 같은 규칙) */
function statusOf(it) {
  if (it.done) return 'DONE'
  const now = serverNow()
  return `${it.date} ${it.time}` < `${ymd(now)} ${hm(now)}` ? 'DELAYED' : 'PLANNED'
}

/** 스캔 파일 이름: 양식_설비_월일.pdf */
const scanName = (it) => `${it.formType}_${it.eq.name.replace(/\s/g, '')}_${it.date.slice(5).replace('-', '')}.pdf`

/** 응답 한 줄 (실제 GET /inspections/schedule 에 요청한 칸) */
function view(it) {
  const paper = it.done && it.source === 'PAPER'
  return {
    scheduleId: it.scheduleId,
    date: it.date,
    time: it.time,
    formType: it.formType,
    equipmentId: it.eq.id,
    equipmentName: it.eq.name,
    equipmentNo: it.eq.code,
    assigneeId: it.who.id,
    assignee: it.who.name,
    status: statusOf(it),
    source: it.done ? it.source : null,
    result: it.done ? it.result : null,
    inspectionId: paper ? 5000 + it.scheduleId : null, // 수기 건: 스캔 원본(ocr_result) 번호
    scanName: paper ? scanName(it) : null,
    doneAt: it.doneAt,
    doneBy: it.done ? it.who.name : null,
  }
}

/** 점검 건 목록: month 'YYYY-MM' 또는 from ~ to (날짜 · 시각 순) */
export function list({ month, from, to } = {}) {
  return ITEMS.filter((it) => (month ? it.date.startsWith(month) : (!from || it.date >= from) && (!to || it.date <= to)))
    .sort((a, b) => (a.date + a.time).localeCompare(b.date + b.time))
    .map(view)
}

/* ───────── 스케줄 추가 (POST /inspections/schedule) ───────── */
const REPEAT_STEP = { DAILY: [1, 'day'], WEEKLY: [7, 'day'], MONTHLY: [1, 'month'] }

export function add({ date, time, formType, equipmentId, assigneeId, repeat = 'ONCE', count = 1 }) {
  const eq = eqById(equipmentId)
  const who = whoById(assigneeId)
  if (!date || !time || !formType || !eq || !who) return { error: { code: 'VALIDATION', message: '날짜 · 시각 · 서류 양식 · 설비 · 점검자를 모두 골라 주세요.' } }
  const step = REPEAT_STEP[repeat]
  const n = step ? Math.max(1, Math.min(31, Number(count) || 1)) : 1
  const made = []
  for (let i = 0; i < n; i++) {
    const d = new Date(Number(date.slice(0, 4)), Number(date.slice(5, 7)) - 1, Number(date.slice(8, 10)))
    if (step?.[1] === 'month') d.setMonth(d.getMonth() + i)
    else if (step) d.setDate(d.getDate() + i * step[0])
    const it = makeItem({ date: ymd(d), time, formType, eq, who })
    ITEMS.push(it)
    made.push(view(it))
  }
  return { data: made }
}

/* ───────── 서류 양식 값 (GET · PUT /inspections/schedule/{id}/form) ───────── */
const notFound = { error: { code: 'NOT_FOUND', message: '점검 건을 찾지 못했습니다.', status: 404 } }
const findItem = (id) => ITEMS.find((it) => it.scheduleId === Number(id))

/** 예시: 불량 항목 (항목 번호 → [이상 내용, 조치 내용]) · 측정값 */
const BAD = {
  F02: { 4: ['벨트 균열', '벨트 교체'], 5: ['덕트 연결부 누설', '연결부 실링 보수'], 7: ['필터 차압 기준 초과', '필터 교체'] },
  F03: { 5: ['접속부 과열 흔적', '볼트 재체결 후 재측정'], 7: ['접지선 부식', '접지선 교체'] },
}
const MEASURE = { F02: { 2: 18.2, 3: 46, 7: 118, 8: 412, 10: 3 }, F03: { 5: 52, 8: 24 } }
const ITEM_COUNT = { F02: 12, F03: 10 }

/** 완료 건의 예시 양식 값 (키는 GET /doc-forms 의 fields.key) */
function sampleValues(it) {
  const f = it.formType
  const keys = Object.keys(BAD[f]).map(Number)
  const bad = it.result === 'NG' ? keys[it.scheduleId % keys.length] : 0
  const checker = it.who.name === '정해준' ? '김도현' : '정해준'
  const v = { inspectedAt: `${it.date}T${it.time}`, inspType: '일상', factory: '울산', equipmentNo: it.eq.code.slice(3), inspector: it.who.name, checker }
  if (f === 'F02') v.zone = '도장'
  else Object.assign(v, { roomName: it.eq.name, voltage: '22.9kV' })
  for (let n = 1; n <= ITEM_COUNT[f]; n++) {
    const no = pad(n)
    v[`item${no}`] = n === bad ? '불량' : '양호'
    if (MEASURE[f][n] != null) v[`item${no}Value`] = String(n === bad ? Math.round(MEASURE[f][n] * 1.6) : MEASURE[f][n])
    if (n === bad) v[`item${no}Note`] = '조치 필요'
  }
  if (bad) {
    const due = new Date(Number(it.date.slice(0, 4)), Number(it.date.slice(5, 7)) - 1, Number(it.date.slice(8, 10)) + 7)
    Object.assign(v, { issue1No: String(bad), issue1Problem: BAD[f][bad][0], issue1Action: BAD[f][bad][1], issue1Due: ymd(due) })
  }
  if (f === 'F02') {
    v.verdict = bad ? '조건부 운전' : '정상 운전'
    v.opinion = bad ? `${BAD[f][bad][0]}, 조치 후 재점검` : '이상 없음'
  } else {
    v.verdict = bad ? '주의' : '정상'
    Object.assign(v, { voltR: '22,860', voltS: '22,910', voltT: '22,880', ampR: '312', ampS: '308', ampT: '315', peakKw: '9,840', loadRate: '62', powerFactor: '96', trTemp: '58', kwh: '1,284,500' })
  }
  return v
}

export function form(id) {
  const it = findItem(id)
  if (!it) return notFound
  const values = it.done ? (VALUES[it.scheduleId] ?? sampleValues(it)) : null
  return { data: { scheduleId: it.scheduleId, formType: it.formType, source: it.done ? it.source : null, values } }
}

/** 전자 작성으로 완료 */
export function saveForm(id, { values, result }) {
  const it = findItem(id)
  if (!it) return notFound
  if (it.done) return { error: { code: 'ALREADY_DONE', message: '이미 완료된 점검입니다.', status: 409 } }
  const now = serverNow()
  Object.assign(it, { done: true, source: 'DIGITAL', result: result === 'NG' ? 'NG' : 'OK', doneAt: `${ymd(now)}T${hm(now)}:00+09:00` })
  VALUES[it.scheduleId] = values
  return { data: view(it) }
}

/** 스케줄 없이 새로 작성 (POST /inspections/records) */
export function createRecord({ formType, equipmentId, date, time, values, result, inspectorId }) {
  const eq = eqById(equipmentId) ?? EQUIPMENTS.find((e) => e.formType === formType)
  // 점검자: 고른 사람 → 양식에 적은 이름 (목록에 없으면 그 이름 그대로)
  const who = whoById(inspectorId) ?? INSPECTORS.find((w) => w.name === values?.inspector) ?? { id: null, name: values?.inspector || INSPECTORS[0].name }
  const now = serverNow()
  const it = makeItem({ date: date || ymd(now), time: time || hm(now), formType, eq, who, done: true })
  Object.assign(it, { source: 'DIGITAL', result: result === 'NG' ? 'NG' : 'OK', doneAt: `${ymd(now)}T${hm(now)}:00+09:00` })
  ITEMS.push(it)
  VALUES[it.scheduleId] = values
  return { data: view(it) }
}
