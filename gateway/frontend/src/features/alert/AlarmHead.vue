<!--
  경보·화재 창 머리 (화면설계안 mAlarm() / mFire() 의 .ahead)
  - 경보: 빨강 '3단계 · 경보' + 제목 + 오른쪽 발생 시각·위치
  - 화재: 진한 빨강, 불꽃 아이콘, '화재 발생' + 위치, 오른쪽 감지 시각 · 경과(1초마다)
  - 바탕이 0.9초마다 진해졌다 밝아짐 (설계안 headDng) — 셧다운이 끝나면 회색 '(정지한 설비)를 정지했습니다'
  - 오른쪽 끝 '화면 보기': 창을 접고 화면을 봄 → 상단 바 빨간 띠를 누르면 다시 열림 (화면설계서 C4)
-->
<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps({
  /** GET /alarms/{id}/response */
  response: { type: Object, required: true },
  fire: { type: Boolean, default: false },
  /** 셧다운 끝 (경보만) */
  stopped: { type: Boolean, default: false },
  /** 경과 'HH:mm:ss' (화재만) */
  elapsed: { type: String, default: '' },
  /** 정지한 설비 이름 ('상도 건조로', 공정 전체면 '도장 공정 전체') — 창이 응답에서 꺼내 줌 */
  stoppedName: { type: String, default: '' },
  /** 유형 이름 '가스' · '화재' (경보만: '3단계 · 가스 경보') — 모르면 '' */
  hazardLabel: { type: String, default: '' },
  /** 위치 '5공장 · 도장 공정 · 상도 건조로' (경보 대응 응답에는 없을 수 있어 창이 알림 목록 줄에서 채움) */
  location: { type: String, default: '' },
})

defineEmits(['collapse'])

const time = computed(() => props.response.raisedAt.slice(11, 19))
</script>

<template>
  <header class="ahead" :class="{ fire, stopped }">
    <AppIcon :name="fire ? 'flame' : stopped ? 'power' : 'alert'" :size="fire ? 40 : 34" />
    <div>
      <template v-if="fire">
        <div class="big">화재 발생</div>
        <div class="sub">{{ location }}</div>
      </template>
      <template v-else>
        <div class="step">{{ stopped ? '셧다운 완료' : `3단계 · ${hazardLabel ? `${hazardLabel} ` : ''}경보` }}</div>
        <div class="title">{{ stopped ? `${stoppedName || '설비'}를 정지했습니다` : response.title }}</div>
      </template>
    </div>
    <div class="right">
      <div class="mono time">{{ time }}{{ fire ? ' 감지' : '' }}</div>
      <div v-if="fire" class="mono small">경과 {{ elapsed }}</div>
      <div v-else class="small">{{ location.split(' · ').slice(0, 3).join(' · ') }}</div>
    </div>
    <button type="button" class="fold" @click="$emit('collapse')"><AppIcon name="cd" :size="16" />화면 보기</button>
  </header>
</template>

<style scoped>
.ahead {
  height: 84px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 28px;
  background: var(--color-level-alarm);
  color: #fff;
  animation: head-dng 0.9s ease-in-out infinite;
  transition: background 400ms;
}

.ahead.fire {
  height: 92px;
  gap: 16px;
  background: #b91c1c;
}

.ahead.stopped {
  background: #475569;
  animation: none;
}

.step {
  font-size: 13px;
  font-weight: 600;
  opacity: 0.9;
}

.title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.big {
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.4px;
}

.sub {
  margin-top: 2px;
  font-size: 14px;
  opacity: 0.95;
}

.right {
  margin-left: auto;
  text-align: right;
}

.time {
  font-size: 20px;
  font-weight: 600;
}

.small {
  font-size: 12.5px;
  opacity: 0.9;
}

.fold {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  margin-left: 8px;
  padding: 0 12px;
  border: 1px solid rgba(255, 255, 255, 0.55);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: background var(--m-hover);
}

.fold:hover {
  background: rgba(255, 255, 255, 0.24);
}

@keyframes head-dng {
  0%,
  100% {
    filter: brightness(1);
  }
  50% {
    filter: brightness(0.82);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ahead {
    animation: none;
  }
}
</style>
