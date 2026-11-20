<!--
  본사 화재 현황 '화재 상세' 카드 (화면설계안 v-dash.js vRegion() 화재일 때 오른쪽, 화면설계서 C5)
  - 머리(빨강): '화재 상세' + '경고 방송 송출 중' + 지금 시각
  - 지역 · 공장 · 공정 · 설비 · 감지 근거(열화상 최고 486 ℃ · 온도 212 ℃ · CO 120 ppm)
  - 열화상(LIVE, VMS 영상이 오면 영상 · 안 오면 사진), 대응 현황(감지 → 경고 방송 → 자동 셧다운 → 본사 통보 → 경보 해제 대기), 차례로 나타남(.09초 간격)
  - 현장에서 해제 비밀번호를 5회 틀려 잠기면 '해제 잠금 풀기(승인)' 단추 (POST /fires/{id}/approve-unlock)
  - 단추: 5공장 대시보드로 이동(그 공정 배치도), 공장 담당자 연락(채팅은 아직 없어 알림 띠만)
-->
<script setup>
import { approveFireUnlock } from '@/api/emergency'
import AppIcon from '@/components/AppIcon.vue'
import BaseCard from '@/components/BaseCard.vue'
import ThermalImage from '@/features/equipment/ThermalImage.vue'
import { liveStreamUrl } from '@/features/equipment/thermalStream'
import { LEVEL } from '@/types/codes'
import { toast } from '@/utils/toast'

const props = defineProps({
  /** GET /fires/{id} */
  fire: { type: Object, required: true },
  /** 지금 시각 'HH:mm:ss' (경과 표시용, 부모가 1초마다 바꿈) */
  now: { type: String, default: '' },
})

const emit = defineEmits(['go'])

const parts = props.fire.location.split(' · ') // [사업장, 공장, 공정, 설비]
const hm = (iso) => iso?.slice(11, 19) ?? '대기'

async function approve() {
  try {
    await approveFireUnlock(props.fire.id)
    toast('해제 잠금을 풀었습니다. 현장에서 다시 비밀번호를 입력할 수 있습니다.')
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
}

function contact() {
  // TODO(채팅 작업): 채팅 패널을 그 공장 담당자 대화로 열기
  toast('공장 담당자 한지우 님에게 연락 요청을 보냈습니다.')
}
</script>

<template>
  <BaseCard class="fire-card">
    <header class="fh">
      <h3>화재 상세</h3>
      <span v-if="fire.broadcast.on" class="m-chip alarm">경고 방송 송출 중</span>
      <span class="mono now">{{ now }}</span>
    </header>

    <dl class="rows">
      <div><dt>지역</dt><dd>{{ parts[0] }}</dd></div>
      <div><dt>공장</dt><dd>{{ parts[1] }}</dd></div>
      <div><dt>공정</dt><dd>{{ parts[2] }}</dd></div>
      <div><dt>설비</dt><dd>{{ parts[3] }}</dd></div>
      <div class="last">
        <dt>감지 근거</dt>
        <dd>
          열화상 최고 <span class="mono">{{ fire.values.thermalMax }}</span> ℃ · 온도 <span class="mono">{{ fire.values.temp }}</span> ℃ ·
          CO <span class="mono">{{ fire.values.co }}</span> ppm
        </dd>
      </div>
    </dl>

    <div class="ir">
      <ThermalImage
        :image-url="fire.thermal.imageUrl"
        :stream-url="liveStreamUrl(fire.thermal, LEVEL.FIRE)"
        :max="fire.thermal.max"
        :max-point="fire.thermal.maxPoint"
        :label-offset="{ x: -150, y: -52 }"
        :scale="fire.thermal.scale"
        :zone-name="[fire.thermal.cameraTag, parts[2]].filter(Boolean).join(' · ')"
      />
    </div>

    <div class="steps">
      <div class="m-sect"><AppIcon name="clock" :size="15" />대응 현황</div>
      <div v-for="(a, i) in fire.actions" :key="a.step" class="stp" :style="{ animationDelay: `${i * 90}ms` }">
        <i :class="{ done: a.done }"><AppIcon name="ok" :size="12" /></i>
        <span :class="{ wait: !a.done }">{{ a.text }}</span>
        <span class="mono">{{ hm(a.at) }}</span>
      </div>
      <div v-if="fire.unlock.locked" class="lock">
        <AppIcon name="lock" :size="16" />현장에서 해제 비밀번호를 5회 틀려 잠겼습니다.
        <button type="button" class="btn btn-primary" @click="approve">잠금 풀기 승인</button>
      </div>
      <div class="m-actions btns">
        <button type="button" class="btn btn-primary" @click="emit('go')">{{ parts[1] }} 대시보드로 이동</button>
        <button type="button" class="btn" @click="contact"><AppIcon name="user" :size="16" />공장 담당자 연락</button>
      </div>
    </div>
  </BaseCard>
</template>

<style scoped>
.fire-card {
  border-color: var(--color-level-alarm);
}

.fh {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid #fecaca;
  background: #fef2f2;
}

.fh h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-level-alarm-text);
}

.now {
  margin-left: auto;
  font-size: 13px;
  color: var(--color-text-sub);
}

/* 지역 · 공장 · 공정 · 설비 (설계안 .fr) */
.rows {
  margin: 0;
  padding: 6px 20px 0;
}

.rows div {
  display: flex;
  gap: 16px;
  padding: 9px 0;
  border-bottom: 1px solid #f1f5f9;
  font-size: 14px;
}

.rows div.last {
  border-bottom: none;
}

.rows dt {
  width: 70px;
  flex: none;
  color: var(--color-text-sub);
}

.rows dd {
  margin: 0;
  font-weight: 600;
}

.ir {
  padding: 8px 20px 0;
}

/* 열화상 높이는 설계안처럼 360px (폭이 넓어도 4:3 으로 커지지 않게) */
.ir :deep(.ir) {
  aspect-ratio: auto;
  height: 360px;
}

.steps {
  padding: 16px 20px 20px;
}

/* 대응 현황 한 줄 (설계안 .stp) */
.stp {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  font-size: 13.5px;
  animation: softin 300ms ease-out both;
}

.stp i {
  width: 20px;
  height: 20px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-border-strong);
  color: #fff;
}

.stp i.done {
  background: var(--color-level-alarm);
}

.stp i .app-icon {
  stroke-width: 3.5;
}

.stp .wait {
  color: #64748b;
}

.stp .mono {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-weak);
}

.lock {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: #fef2f2;
  font-size: 13px;
  color: var(--color-level-alarm-text);
}

.lock .btn {
  margin-left: auto;
  height: 34px;
  font-size: 13px;
}

.btns {
  margin-top: 14px;
}

.btns .btn {
  flex: 1;
  height: 44px;
}
</style>
