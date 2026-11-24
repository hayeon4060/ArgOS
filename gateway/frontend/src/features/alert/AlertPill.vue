<!--
  상단 바 상태 띠 (화면설계안 js/shell.js updateHeader() #stpill, css/extra.css)
  - 대응 중 알림이 있을 때만: '⚠ 주의 1건  상도 건조로 · 눌러서 확인'
  - 나타날 때 튀어 오름(.4s) 후 1.6초마다 주황 빛번짐, 아이콘은 1초마다 깜빡임
  - 화재: '🔥 화재 발생  상도 건조로 버너실' (설계안)
  - 대응 중인 알림이 여러 건이면 (2026-11-19 재원님 요청): '주의 2건  상도 건조로 외 1건 · 눌러서 선택 ⌄'
    누르면 띠 아래로 목록(AlertPillList)이 미끄러져 내려오고, 한 건을 고르면 그 알림 창이 열림
    목록은 바깥을 누르거나 Esc, 다시 띠를 누르면 닫힘
  - 누르면(한 건일 때 · 목록에서 골랐을 때): 공장 담당자 → 알림 상태에 맞는 창 (주의 알림 / 주의 상세 / 경보·화재 대응)
    체크리스트를 보낸 뒤(조치 중)에도 주의 상세 (2026-11-22 재원님 요청, 체크리스트는 상단 바 '체크리스트 진행' 단추)
  - 같은 설비에 경보·화재가 진행 중이면 그 설비의 주의는 띠·목록에 안 나옴 (2026-11-22 재원님 요청: 2중 알림)
            본사 관리자 → 화재면 사업장 선택(화재 현황), 그 밖에는 그 공정 배치도 화면으로 (조치는 공장 담당자 몫)
  - 색: 재원님이 정한 주의색 #F97316 (설계안은 띠 바탕만 조금 진한 #EA580C)
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AlertPillList from './AlertPillList.vue'
import AppIcon from '@/components/AppIcon.vue'
import { useAlertStore } from '@/stores/alert'
import { LEVEL, LEVEL_META } from '@/types/codes'

const router = useRouter()
const alert = useAlertStore()

const a = computed(() => alert.primary)
const meta = computed(() => LEVEL_META[a.value?.level] ?? null)
// 건수: 같은 설비 경보 중인 주의는 뺀 목록으로 (stores/alert.js visibleAlarms)
const count = computed(() => alert.visibleAlarms.filter((x) => x.level === a.value?.level).length)
const isFire = computed(() => a.value?.level === LEVEL.FIRE)
// 설비 이름: 위치의 마지막 칸에서 부품 이름을 뺌 ('상도 건조로 배기팬' → '상도 건조로'), 화재는 부품까지
const where = computed(() => {
  const last = a.value?.location.split(' · ').at(-1) ?? ''
  return isFire.value ? last : last.replace(/ (배기팬|버너실|순환펌프)$/, '')
})

/* ───────── 여러 건일 때 내려오는 목록 ───────── */
const total = computed(() => alert.visibleAlarms.length)
const many = computed(() => total.value > 1)
const listOpen = ref(false)
const root = ref(null)

/** 알림 한 건 열기: 공장 담당자는 알림 창, 본사 관리자는 그 위치 화면 */
function go(alarm) {
  listOpen.value = false
  if (alert.canAct) {
    alert.openFor(alarm)
    return
  }
  if (alarm.level === LEVEL.FIRE) {
    router.push({ name: 'site-select' })
    return
  }
  const { siteId, factoryId, processId } = alarm
  router.push({ name: 'process', params: { siteId, factoryId, processId } })
}

/** 띠 누르기: 여러 건이면 목록을 열고 닫음, 한 건이면 바로 엶 */
function onClick() {
  if (many.value) listOpen.value = !listOpen.value
  else go(a.value)
}

// 한 건 이하로 줄면 목록을 닫음
watch(many, (v) => v || (listOpen.value = false))

// 목록 바깥을 누르거나 Esc 를 누르면 닫음
const onOutside = (e) => listOpen.value && !root.value?.contains(e.target) && (listOpen.value = false)
const onKey = (e) => e.key === 'Escape' && (listOpen.value = false)
onMounted(() => {
  document.addEventListener('pointerdown', onOutside)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onOutside)
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <Transition name="pill">
    <div v-if="a && meta" :key="a.level" ref="root" class="wrap">
      <button
        type="button"
        class="pill"
        :class="[meta.key, `blink-${meta.key}`]"
        role="status"
        :aria-haspopup="many ? 'menu' : undefined"
        :aria-expanded="many ? listOpen : undefined"
        @click="onClick"
      >
        <AppIcon :name="meta.icon" :size="18" class="ico" />
        <template v-if="isFire">화재 발생</template>
        <template v-else>{{ meta.label }} {{ count }}건</template>
        <!-- 여러 건: '상도 건조로 외 1건 · 눌러서 선택' + 펼침 화살표 -->
        <small v-if="many">{{ where }} 외 {{ total - 1 }}건 · 눌러서 선택</small>
        <small v-else>{{ where }}{{ isFire ? '' : ' · 눌러서 확인' }}</small>
        <AppIcon v-if="many" name="cd" :size="16" class="more" :class="{ up: listOpen }" />
      </button>

      <!-- 아래로 미끄러져 내려오는 목록 (단계 높은 순) -->
      <Transition name="slide">
        <AlertPillList v-if="many && listOpen" class="drop" :alarms="alert.sortedAlarms" @pick="go" />
      </Transition>
    </div>
  </Transition>
</template>

<style scoped>
/* 띠 + 목록을 감싸는 칸 (목록 위치의 기준) */
.wrap {
  position: relative;
  display: flex;
  min-width: 0;
}

/* 목록: 상단 바 아랫선에 붙어서 내려옴 (띠 38px + 상단 바 남는 높이) */
.drop {
  position: absolute;
  left: 0;
  top: calc(100% + (var(--topbar-height) - 38px) / 2);
  z-index: 20;
}

/* 미끄러져 내려오기: 위에서부터 220ms 동안 펼쳐짐 (그림자가 잘리지 않게 아래·옆은 넉넉히) */
.slide-enter-active,
.slide-leave-active {
  transition:
    clip-path 220ms ease-out,
    transform 220ms ease-out;
}

.slide-enter-from,
.slide-leave-to {
  clip-path: inset(0 -40px 100% -40px);
  transform: translateY(-10px);
}

.slide-enter-to,
.slide-leave-from {
  clip-path: inset(0 -40px -40px -40px);
}

/* 펼침 화살표: 열리면 뒤집힘 */
.more {
  flex: none;
  transition: transform 150ms;
}

.more.up {
  transform: rotate(180deg);
}

.pill {
  height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px 0 10px;
  border: none;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 13.5px;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
  transition: transform 150ms;
  /* 상단 바가 1920 보다 좁아질 때 '어디 · 눌러서 확인' 글자부터 줄여서(…) 화면 밖으로 밀리지 않게 */
  min-width: 0;
  overflow: hidden;
}

.pill:hover {
  transform: translateY(-1px);
}

.pill small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12.5px;
  font-weight: 500;
  opacity: 0.95;
}

.pill .ico {
  stroke-width: 2.4;
  animation: pill-blink 1s steps(2, jump-none) infinite;
}

/*
 * 나타날 때 커지며 등장 + 버튼 밖으로 넓게 퍼지는 링 2겹 (2026-11-20 재원님 선택, blink.css blink-ring)
 * - 링 반복: 주의 2초 · 경보 1초 · 화재 0.75초 (단계 반복 시간 × 1.25), 퍼지는 폭 8 · 11 · 14px
 */
.pill.warn,
.pill.alarm,
.pill.fire {
  box-shadow: 0 2px 10px rgb(var(--blink-rgb) / 0.35);
  animation:
    pill-pop 400ms cubic-bezier(0.2, 1.2, 0.3, 1) both,
    blink-ring calc(var(--blink-t) * 1.25) cubic-bezier(0.2, 0.6, 0.3, 1) 400ms infinite;
}

.pill.warn {
  background: var(--color-level-warn);
}

.pill.alarm,
.pill.fire {
  background: var(--color-level-alarm);
}

/* 마우스 올림: 링 멈추고 진한 테두리로 고정 (눌러도 되는 곳) */
.pill:hover {
  animation: none;
  box-shadow: 0 0 0 4px rgb(var(--blink-rgb) / 0.3);
}

/* 사라질 때 */
.pill-leave-active {
  transition: opacity var(--m-pop-out);
}

.pill-leave-to {
  opacity: 0;
}

@keyframes pill-pop {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes pill-blink {
  0% {
    opacity: 1;
  }
  100% {
    opacity: 0.35;
  }
}

/* 좁은 화면: 설명 글자는 숨기고 '주의 1건' 은 끝까지 보이게 (줄어들지 않음) */
@media (max-width: 1600px) {
  .wrap,
  .pill {
    flex-shrink: 0;
  }

  .pill small {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .pill,
  .pill .ico {
    animation: none !important;
  }

  /* 링 대신 굵은 바깥 테두리로 고정 */
  .pill {
    box-shadow:
      0 0 0 2px #fff,
      0 0 0 4px rgb(var(--blink-rgb) / 0.6);
  }

  .slide-enter-active,
  .slide-leave-active {
    transition: none;
  }
}
</style>
