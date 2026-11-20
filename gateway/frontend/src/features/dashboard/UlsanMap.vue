<!--
  울산공장 구역 지도 (공장 건물 6개 + 이름표)
  - 그림: 화면설계안 assets/svgs.js 의 울산공장 지도를 정적 SVG 로 만든 것 (assets/images/ulsan-map.svg)
  - 공장 상태에 따라 구역 색(정상 초록 / 주의 주황 / 경보 빨강)과 이름표 글자를 바꿈
    (설계안 lib.js ulsanMap() 과 같은 방식: SVG 안 .zone 요소의 data-level 을 바꿈)
  - 2026-11-20 재원님 요청: 공장 카드(공정 목록)는 마우스를 올리면, 공장 화면 이동은 누르면
    · 구역에 마우스를 올리면 hover 이벤트: { code, anchor: { x, y } } — anchor 는 구역 가운데 위치(%)
    · 지도 밖으로 마우스가 나가면 hover(null)
    · 구역을 누르면 select 이벤트: 공장 코드 (빈 곳 클릭은 아무것도 안 함)
-->
<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import ulsanSvg from '@/assets/images/ulsan-map.svg?raw'
import { LEVEL, LEVEL_META } from '@/types/codes'

const props = defineProps({
  /** 사업장 현황의 공장 목록 [{ code, level, ... }] */
  factories: { type: Array, required: true },
})

const emit = defineEmits(['hover', 'select'])

/**
 * 지도 그림의 구역 이름 ↔ 공장 코드
 * - 지도 그림이 설계안 고정 그림이라 프론트에 짝을 적어 둠 (명세서의 mapShape 는 아직 안 씀)
 */
const ZONE_BY_CODE = { P1: 'plant1', P2: 'plant2', P3: 'plant3', P4: 'plant4', P5: 'plant5', EV: 'engine' }
const CODE_BY_ZONE = Object.fromEntries(Object.entries(ZONE_BY_CODE).map(([code, zone]) => [zone, code]))

// 지도 SVG 의 viewBox (ulsan-map.svg 와 같은 값) — 구역 위치를 % 로 바꿀 때 사용
const VIEWBOX = { x: 68, y: 72, w: 1545, h: 715 }

// 상태 → 지도 그림이 쓰는 이름 (설계안 SVG 의 data-level 값)
const ZONE_LEVEL = {
  [LEVEL.NORMAL]: 'normal',
  [LEVEL.WARN]: 'warn',
  [LEVEL.ALARM]: 'danger',
  [LEVEL.FIRE]: 'danger',
}

// 이름표 동그라미 안 그림 (정상: 체크, 그 외: 느낌표) — 설계안과 같은 선
const BADGE_PATH = {
  normal: 'M-4 0.2l2.7 2.8 5.3-5.6',
  warn: 'M0 -4.2v4.4M0 3.4v.4',
  danger: 'M0 -4.2v4.4M0 3.4v.4',
}

const box = ref(null)

/** 공장 상태를 지도 구역에 반영 (같은 구역이 건물·이름표 두 군데 있어서 모두 바꿈) */
function applyLevels() {
  const svg = box.value?.querySelector('svg')
  if (!svg) return
  for (const factory of props.factories) {
    const zone = ZONE_BY_CODE[factory.code]
    if (!zone) continue
    const level = ZONE_LEVEL[factory.level] ?? 'normal'
    svg.querySelectorAll(`.zone[data-zone="${zone}"]`).forEach((el) => {
      el.setAttribute('data-level', level)
      const text = el.querySelector('text.lv')
      if (text) text.textContent = LEVEL_META[factory.level]?.label ?? ''
      const badge = el.querySelector('g[transform] path')
      if (badge) badge.setAttribute('d', BADGE_PATH[level])
    })
  }
}

/** 구역 가운데 위치를 지도 크기 대비 % 로 (말풍선 위치용) */
function anchorOf(zoneEl) {
  const b = zoneEl.getBBox()
  return {
    x: ((b.x + b.width / 2 - VIEWBOX.x) / VIEWBOX.w) * 100,
    y: ((b.y + b.height * 0.55 - VIEWBOX.y) / VIEWBOX.h) * 100,
  }
}

/** 구역 위치 구하기 (부모가 처음에 말풍선을 띄울 때 사용) */
function anchorOfCode(code) {
  const el = box.value?.querySelector(`.zone[data-zone="${ZONE_BY_CODE[code]}"]`)
  return el ? anchorOf(el) : null
}
defineExpose({ anchorOfCode })

/** 이벤트가 난 곳의 구역 이름 (구역 밖이면 null) */
const zoneOf = (event) => event.target.closest('.zone')?.getAttribute('data-zone') ?? null

// 지금 마우스가 올라가 있는 구역 — 같은 구역 안에서 움직일 때는 이벤트를 다시 보내지 않음
let hoveredZone = null

/**
 * 마우스 올림: 구역이 바뀔 때만 hover 이벤트
 * - mouseover 사용 (mouseout 은 SVG 안 작은 요소를 지날 때마다 나서 카드가 깜빡임)
 * - 구역 사이 빈 곳(길·배경)에 올리면 카드를 닫음
 */
function onMouseOver(event) {
  const zone = zoneOf(event)
  if (zone === hoveredZone) return
  hoveredZone = zone
  if (!zone) {
    emit('hover', null)
    return
  }
  // 이름표 쪽 구역에 올려도 위치는 건물 쪽 구역 기준으로 맞춤
  const first = box.value.querySelector(`.zone[data-zone="${zone}"]`)
  emit('hover', { code: CODE_BY_ZONE[zone], anchor: anchorOf(first) })
}

/** 지도 밖으로 나가면 카드 닫기 */
function onMouseLeave() {
  hoveredZone = null
  emit('hover', null)
}

/** 지도 클릭: 구역이면 그 공장 코드로 select (빈 곳은 무시) */
function onClick(event) {
  const zone = zoneOf(event)
  if (zone) emit('select', CODE_BY_ZONE[zone])
}

onMounted(applyLevels)
watch(() => props.factories, () => nextTick(applyLevels), { deep: true })
</script>

<template>
  <!-- SVG 문자열이 저장소 안 고정 그림이라 v-html 사용 (사용자 입력 아님) -->
  <div
    ref="box"
    class="ulsan-map-box"
    @mouseover="onMouseOver"
    @mouseleave="onMouseLeave"
    @click="onClick"
    v-html="ulsanSvg"
  />
</template>

<style scoped>
.ulsan-map-box {
  width: 100%;
  height: 100%;
}

/* v-html 로 넣은 SVG 에는 scoped 스타일이 안 닿아서 :deep() 사용 */
.ulsan-map-box :deep(svg) {
  display: block;
  width: 100%;
  height: 100%;
}

/* 공장 구역은 누를 수 있다는 표시 */
.ulsan-map-box :deep(.zone) {
  cursor: pointer;
}
</style>
