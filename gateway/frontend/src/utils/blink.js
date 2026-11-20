/**
 * 주의·경보·화재 점멸 도우미 (모양은 assets/styles/blink.css)
 * - blinkClass(level): 단계 값 클래스 ('blink-warn' 등, 정상이면 '')
 * - blinkKey(level)  : SVG data-blink 값 ('warn' | 'alarm' | 'fire', 정상이면 null)
 * - startBlinkSync() : 같은 단계 점멸의 박자를 맞춤 (main.js 에서 한 번 실행)
 */
import { LEVEL } from '@/types/codes'

const KEY = { [LEVEL.WARN]: 'warn', [LEVEL.ALARM]: 'alarm', [LEVEL.FIRE]: 'fire' }

/** 'WARN' → 'warn', 정상·없음 → null */
export const blinkKey = (level) => KEY[level] ?? null

/** 'WARN' → 'blink-warn', 정상·없음 → '' */
export const blinkClass = (level) => (KEY[level] ? `blink-${KEY[level]}` : '')

/**
 * 박자 맞추기
 * - CSS 애니메이션은 요소가 화면에 나타난 순간부터 돌아서, 늦게 나타난 카드·말풍선은 박자가 어긋남
 *   (예: 지도 구역은 밝을 때 옆 카드는 어두움 → 산만해 보임)
 * - 0.5초마다 이름이 'blink-' 로 시작하는 애니메이션의 시작 시각을 문서 시작(0)으로 맞춤
 *   → 같은 단계(같은 반복 시간)는 모두 같은 순간에 밝아지고 어두워짐
 * - 이미 맞춘 것은 건드리지 않아서 부담이 거의 없음
 */
export function startBlinkSync() {
  if (typeof document.getAnimations !== 'function') return
  setInterval(() => {
    for (const a of document.getAnimations()) {
      if (a.animationName?.startsWith('blink-') && a.playState === 'running' && a.startTime !== 0) a.startTime = 0
    }
  }, 500)
}
