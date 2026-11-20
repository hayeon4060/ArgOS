/**
 * 알림 위치 · 이름 꺼내기 (알림 창들이 같이 씀) — 화면 코드에 공정 번호·설비 이름을 고정하지 않으려고
 *
 * 백엔드 응답 (develop AlarmViews · AlarmService)
 * - 알림 목록 한 줄(GET /alarms): siteId, factoryId, processId, equipmentId, unitName('상도 건조로'),
 *   location '5공장 · 도장 공정 · 상도 건조로 배기팬' (사업장 이름 없음)
 * - 주의 상세(GET /alarms/{id}/detail): processId, equipmentId, location 은 사업장 이름부터
 * - 경보 대응(GET /alarms/{id}/response): 위치 번호가 없음 → 같은 알림의 목록 줄(stores/alert activeAlarms)에서 꺼냄
 */
import { getSiteName } from '@/api/sites'
import { sensorTypeOf } from '@/types/codes'

/** 위치 문구에서 부품 이름 빼기 ('상도 건조로 배기팬' → '상도 건조로') */
export const stripPart = (name = '') => name.replace(/ (배기팬|버너실|순환펌프)$/, '')

/**
 * 알림 목록 한 줄 → 화면에 쓸 위치
 * @param {object|null} alarm GET /alarms 의 items 하나
 * @returns {{ siteId, factoryId, processId, factoryName, processName, equipmentName }}
 */
export function placeOf(alarm) {
  const parts = (alarm?.location ?? '').split(' · ')
  // location 이 '울산공장 · 5공장 · …' 처럼 사업장부터면 한 칸 밀림
  const offset = parts.length >= 4 ? 1 : 0
  return {
    siteId: alarm?.siteId ?? null,
    factoryId: alarm?.factoryId ?? null,
    processId: alarm?.processId ?? null,
    factoryName: parts[offset] ?? '',
    processName: parts[offset + 1] ?? '',
    equipmentName: alarm?.unitName ?? stripPart(parts[offset + 2] ?? ''),
  }
}

/* ───────── 사업장 이름 (알림 목록 location 에는 없어서 siteId 로 한 번만 받음) ───────── */
const siteNames = new Map()

/** @returns {Promise<string>} 못 받으면 '' */
export async function siteNameOf(siteId) {
  if (!siteId) return ''
  if (!siteNames.has(siteId)) siteNames.set(siteId, getSiteName(siteId).catch(() => ''))
  return siteNames.get(siteId)
}

/* ───────── 알림 창 작은 추이 그래프 제목 ───────── */
// 센서 이름 앞 글자로 종류 찾기 (응답에 종류가 없을 때)
const TYPE_BY_PREFIX = { CT: 'CURRENT', TE: 'TEMP', CO: 'CO' }

/**
 * 주의 상세의 알림 센서 종류 ('CURRENT' | 'TEMP' | 'CO')
 * - 백엔드 detail 에는 sensorType 이 없음 → rule.sensorTag 로 sensors 에서 찾고, 없으면 이름 앞 글자
 */
export function sensorTypeOfDetail(detail) {
  const tag = detail?.trend?.tag ?? detail?.rule?.sensorTag ?? ''
  return detail?.sensorType ?? detail?.sensors?.find((s) => s.tag === tag)?.type ?? TYPE_BY_PREFIX[tag.split('-')[0]] ?? null
}

/* 유형별로 보여 줄 추이 2개 (2026-11-19 재원님 요청) — 화재: 온도·전류 / 가스: CO·전류 */
const TREND_TYPES = { FIRE: ['TEMP', 'CURRENT'], GAS: ['CO', 'CURRENT'] }

/**
 * 주의 상세 아래 추이 그래프 목록
 * - 응답 trends[] (센서별 추이) 에서 유형에 맞는 2개를 순서대로 고름
 * - trends 가 없으면(백엔드 반영 전) 지금처럼 trend 하나만
 * TODO(백엔드): GET /alarms/{id}/detail 에 trends[] 추가 (docs/api/warn-detail-trends.md)
 * @param {object|null} detail
 * @param {'FIRE'|'GAS'|null} hazard
 * @returns {Array<{ trend: object, title: string, tag: string, dec: number }>}
 */
export function trendsOf(detail, hazard) {
  if (!detail) return []
  const typeOf = (t) => t.type ?? detail.sensors?.find((s) => s.tag === t.tag)?.type ?? TYPE_BY_PREFIX[(t.tag ?? '').split('-')[0]] ?? null
  const list = detail.trends?.length ? detail.trends : detail.trend ? [detail.trend] : []
  const wanted = TREND_TYPES[hazard]
  // 유형에 맞는 종류만, 정해 둔 순서대로 (하나도 못 찾으면 받은 그대로)
  const picked = wanted ? wanted.map((type) => list.find((t) => typeOf(t) === type)).filter(Boolean) : []
  return (picked.length ? picked : list).map((t) => {
    const meta = sensorTypeOf(typeOf(t))
    return { trend: t, title: `${meta?.label ?? '센서'} 추이`, tag: t.tag ?? '', dec: meta?.dec ?? 1 }
  })
}
