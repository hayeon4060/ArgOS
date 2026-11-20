/**
 * 열화상 실시간 영상 주소 고르기
 * - VMS 는 3단계(경보 · 화재)일 때만 mp4 영상을 내줌 (http://{VMS}:9000/stream/{카메라}) — 그 밖의 단계는 404
 * - 서버는 GET /thermal/{id}/latest 의 streamUrl, GET /fires/{id} 의 thermal.streamUrl 로 이 주소를 줌
 * - 3단계가 아니면 null → ThermalImage 가 지금처럼 사진을 보여 줌
 * - 단계는 열화상 응답의 level 과 화면이 아는 단계(설비 · 경보 · 화재) 중 하나라도 3단계면 영상으로 봄
 *   (영상이 실제로 안 나오면 ThermalImage 가 사진으로 되돌림)
 * - 브라우저 <video> 는 http(s) 주소만 틀 수 있음. rtsp:// 같은 주소가 오면 영상 없이 사진만
 *   (2026-11-20 실제 서버가 'rtsp://vms-server/ir-301' 을 준 적이 있음 → DB 의 sensor.stream_url 을 http 주소로 넣어야 영상이 나옴)
 */
import { LEVEL } from '@/types/codes'

const STAGE3 = [LEVEL.ALARM, LEVEL.FIRE]

/**
 * @param {{ streamUrl?: string|null, level?: string }|null} thermal 열화상 응답
 * @param {...string} levels 화면이 아는 단계 (예: 설비 단계, 경보 창 단계)
 * @returns {string|null} 영상 주소 또는 null
 */
export function liveStreamUrl(thermal, ...levels) {
  if (!/^https?:\/\//i.test(thermal?.streamUrl ?? '')) return null
  const stage3 = [thermal.level, ...levels].some((lv) => STAGE3.includes(lv))
  return stage3 ? thermal.streamUrl : null
}
