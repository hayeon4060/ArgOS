<!--
  주의 알림 창 (화면설계안 js/v-modals.js mWarn(), 화면설계서 C1)
  - 위쪽 주황 띠, 숨쉬듯 빛나는 경고 아이콘, '가스 주의 상태가 감지되었습니다'(CO) · '화재 주의 …'(전류·온도), 위치
  - 센서 · 지금 값 / 주의 기준 / 발생 시각
  - 가스(CO) 주의면 센서 칸 아래 '배기팬 자동 가동 75%' 줄 (알림의 fanAuto, FanAutoNotice) — 2026-11-19
  - 2초 길게 누르기 → POST /alarms/{id}/ack → 주의 상세 창으로 바뀜
  - 꼭 확인해야 하는 창이라 바깥 누르기·Esc 로 안 닫힘 (lock, 누르면 흔들림)
-->
<script setup>
import { computed, ref } from 'vue'
import { ackAlarm } from '@/api/alarms'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'
import HoldButton from '@/components/HoldButton.vue'
import FanAutoNotice from './FanAutoNotice.vue'
import { siteNameOf } from './alarmPlace'
import { hazardLabelOf } from './alarmView'
import { useAlertStore } from '@/stores/alert'
import { useAuthStore } from '@/stores/auth'
import { sensorTypeOf } from '@/types/codes'
import { toast } from '@/utils/toast'

const props = defineProps({
  /** 대응 중 알림 한 건 (GET /alarms 의 items 하나) */
  alarm: { type: Object, required: true },
})

const alert = useAlertStore()
const auth = useAuthStore()

const type = computed(() => sensorTypeOf(props.alarm.sensorType))
const time = computed(() => props.alarm.raisedAt.slice(11, 19))
// 유형 문구: CO 가 높으면 '가스 주의', 전류·온도가 높으면 '화재 주의'
const kind = computed(() => [hazardLabelOf(props.alarm), '주의'].filter(Boolean).join(' '))
// 위치 앞에 사업장 이름 (알림 응답의 location 은 공장부터 → siteId 로 사업장 이름을 받음)
const siteName = ref('')
siteNameOf(props.alarm.siteId).then((n) => (siteName.value = n))
const location = computed(() => [siteName.value, props.alarm.location].filter(Boolean).join(' · '))

/** 누른 시간 '1.2 / 2.0초' */
const heldMs = ref(0)

async function onDone() {
  try {
    await ackAlarm(props.alarm.id, auth.user?.name)
    alert.open('detail', { alarmId: props.alarm.id })
  } catch (error) {
    toast(error.message, { type: 'err' })
    alert.close()
  }
}
</script>

<template>
  <BaseModal :width="520" lock tone="warn" label="주의 알림">
    <div class="band" />
    <div class="body">
      <div class="ico"><AppIcon name="tri" :size="30" /></div>
      <div class="step">2단계 · {{ kind }}</div>
      <h2>{{ kind }} 상태가 감지되었습니다</h2>
      <div class="loc">{{ location }}</div>

      <div class="m-box cells">
        <div>
          <small>{{ type?.label }} <span class="mono">{{ alarm.sensorTag }}</span></small>
          <b class="mono warn">{{ alarm.value }} <i>{{ alarm.unit }}</i></b>
        </div>
        <div>
          <small>주의 기준</small>
          <b class="mono">{{ alarm.threshold }} <i>{{ alarm.unit }}</i></b>
        </div>
        <div>
          <small>발생 시각</small>
          <b class="mono">{{ time }}</b>
        </div>
      </div>

      <!-- 가스 주의: 배기팬 자동 제어 결과 (자동이면 75%로 올림, 수동이면 승인 대기 · 승인 · 거절 버튼) + 속도 직접 조절 20 ~ 60 Hz -->
      <FanAutoNotice v-if="alarm.fanAuto" :fan="alarm.fanAuto" tone="warn" adjustable class="fan" />

      <div class="hold-wrap">
        <HoldButton :size="148" color="#F97316" label="길게 눌러 확인" @progress="heldMs = $event" @done="onDone" />
      </div>
      <p class="guide">2초간 길게 누르면 원인과 체크리스트가 열립니다</p>
      <p class="mono held">{{ (heldMs / 1000).toFixed(1) }} / 2.0초</p>
    </div>
  </BaseModal>
</template>

<style scoped>
.band {
  height: 6px;
  background: var(--color-level-warn);
}

.body {
  padding: 34px 40px 30px;
  text-align: center;
}

/* 경고 아이콘: 주황 빛이 1.4초마다 퍼짐 (설계안 icoWarn) */
.ico {
  width: 60px;
  height: 60px;
  margin: 0 auto;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-level-warn-soft);
  color: #ea580c;
  animation: ico-warn 1.4s ease-in-out infinite;
}

.step {
  margin-top: 14px;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-level-warn-text);
}

h2 {
  margin-top: 4px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.4px;
}

.loc {
  margin-top: 8px;
  font-size: 14px;
  color: var(--color-text-sub);
}

.cells {
  display: flex;
  margin-top: 18px;
  text-align: left;
}

.cells > div {
  flex: 1;
  padding: 12px 16px;
  border-right: 1px solid var(--color-border);
}

.cells > div:last-child {
  border-right: none;
}

.cells small {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.cells b {
  display: block;
  margin-top: 2px;
  font-size: 24px;
  font-weight: 600;
}

.cells b.warn {
  color: var(--color-level-warn-text);
}

.cells i {
  font-style: normal;
  font-size: 13px;
  color: var(--color-text-weak);
}

.fan {
  margin-top: 10px;
}

.hold-wrap {
  display: flex;
  justify-content: center;
  margin-top: 22px;
}

.guide {
  margin-top: 14px;
  font-size: 14px;
  font-weight: 600;
}

.held {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-weak);
}

@keyframes ico-warn {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(249, 115, 22, 0.45);
  }
  60% {
    box-shadow: 0 0 0 16px rgba(249, 115, 22, 0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ico {
    animation: none;
  }
}
</style>
