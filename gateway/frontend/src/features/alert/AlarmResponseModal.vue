<!--
  경보 · 화재 대응 창 (화면설계안 js/v-modals.js mAlarm() · mFire(), 화면설계서 C4 '한 창이 경보·화재 두 모양')
  - 경보: 빨간 머리 / 열화상 + 경보 사유 | 센서 데이터(게이지 3) | 문제 로그 / 설비 셧다운(ShutdownPanel)
    가스(CO) 경보면 경보 사유 아래 '배기팬 자동 가동 100%' 줄 (응답 fanAuto, FanAutoNotice) — 2026-11-19
  - 화재: 진한 빨간 머리 + 경과 / 경고 방송 띠 / 열화상 | 감지 근거 숫자 3칸 + 자동 조치 / 경보 해제(FireRelease)
  - 꼭 처리해야 하는 창이라 바깥 누르기·Esc 로 안 닫힘 (흔들림). '설비 배치도에서 보기'로 접으면 상단 바 빨간 띠로 다시 열 수 있음
  - 데이터: GET /alarms/{id}/response (+ 화재면 GET /fires/{fireId}). 바뀌면(SSE, 지금은 예시 상황) 다시 불러옴
  - 본사 관리자: 열람만 (셧다운·해제 단추 없음)
  - 공정 번호·이름, 설비 이름은 고정하지 않음: 응답에 위치 번호가 없어 같은 알림의 목록 줄(GET /alarms)에서 꺼냄 (alarmPlace.js)
-->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AlarmHead from './AlarmHead.vue'
import { hazardLabelOf } from './alarmView'
import { placeOf } from './alarmPlace'
import BroadcastBand from './BroadcastBand.vue'
import EmergencySensors from './EmergencySensors.vue'
import FanAutoNotice from './FanAutoNotice.vue'
import FireRelease from './FireRelease.vue'
import ProblemLog from './ProblemLog.vue'
import ShutdownPanel from './ShutdownPanel.vue'
import { callWorkers, getAlarmResponse, getFire } from '@/api/emergency'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'
import ThermalImage from '@/features/equipment/ThermalImage.vue'
import { useAlertStore } from '@/stores/alert'
import { useLiveReload } from '@/utils/useLiveReload'
import { clearToasts, toast } from '@/utils/toast'

const props = defineProps({
  alarmId: { type: Number, required: true },
})

const router = useRouter()
const route = useRoute()
const alert = useAlertStore()

const response = ref(null)
const fire = ref(null)
const errorMessage = ref('')
let serverOffset = 0

async function load() {
  try {
    const r = await getAlarmResponse(props.alarmId)
    // 화재면 화재 내용까지 받은 뒤 한 번에 넣음
    // (응답만 먼저 넣으면 화재 창이 잠깐 경보 창 모양으로 떴다가 바뀜 — 2026-11-19 재원님이 찾은 버그)
    const f = r.fireId ? await getFire(r.fireId) : null
    if (f) serverOffset = new Date(f.serverTime ?? Date.now()).getTime() - Date.now()
    fire.value = f
    response.value = r
    errorMessage.value = ''
  } catch (error) {
    // 대응하던 알림이 없어짐(다른 사람이 종료 · 상황 초기화) → 잠긴 창이 '없는 알림'으로 남지 않게 닫음
    if (response.value) alert.close()
    else errorMessage.value = error.message
  }
}

onMounted(load)
useLiveReload(load)

/* ───────── 이 알림의 위치 (사업장·공장·공정 번호, 공정·설비 이름) ───────── */
// 알림 목록 줄은 경보가 끝나면 목록에서 빠지므로, 보이는 동안 한 번 잡아 둠
const row = ref(null)
watchEffect(() => {
  const a = alert.activeAlarms.find((x) => x.id === props.alarmId)
  if (a) row.value = a
})
const place = computed(() => {
  const p = placeOf(row.value ?? { location: response.value?.location })
  const r = route.params // 목록 줄이 없으면 지금 보고 있는 화면 주소에서
  return { ...p, siteId: p.siteId ?? r.siteId, factoryId: p.factoryId ?? r.factoryId, processId: p.processId ?? r.processId }
})
// 머리 위치 문구: 화재는 화재 응답, 경보는 응답 → 없으면 목록 줄 location
// 유형 문구 '가스 경보' · '화재 경보': 응답의 hazard, 없으면 알림 목록 줄의 센서 종류로
const hazardLabel = computed(() => hazardLabelOf({ hazard: response.value?.hazard ?? row.value?.hazard, sensorType: row.value?.sensorType }))
const location = computed(() => fire.value?.location ?? response.value?.location ?? row.value?.location ?? '')
// 정지한 설비 이름: 셧다운한 설비들 (공정 전체면 '도장 공정 전체'), 모르면 알림 설비
const stoppedName = computed(() => {
  const ids = response.value?.shutdown?.equipmentIds ?? []
  if (response.value?.shutdown && !ids.length) return `${place.value.processName} 전체`
  const names = (response.value?.equipment ?? []).filter((e) => ids.includes(e.id)).map((e) => e.name)
  return names.join(', ') || place.value.equipmentName
})
// 열화상 아래 이름 'IR-01 · 도장 공정'
const zoneOf = (thermal) => [thermal?.cameraTag, place.value.processName].filter(Boolean).join(' · ')
// 화재 감지 근거 칸의 센서 이름 (응답 센서에서 종류로 찾음)
const tagOf = (type) => response.value?.sensors?.find((s) => s.type === type)?.tag ?? ''

const isFire = computed(() => !!fire.value)
const stopped = computed(() => !isFire.value && !!response.value?.shutdown?.done && !response.value.shutdown.auto)

/* ───────── 화재 경과 시간 (1초마다) ───────── */
const now = ref(Date.now())
const timer = setInterval(() => (now.value = Date.now()), 1000)
onBeforeUnmount(() => clearInterval(timer))
const elapsedSec = computed(() =>
  fire.value ? Math.max(0, Math.floor((now.value + serverOffset - new Date(fire.value.detectedAt).getTime()) / 1000)) : 0,
)
const pad = (n) => String(n).padStart(2, '0')
const elapsed = computed(() => {
  const s = elapsedSec.value
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor(s / 60) % 60)}:${pad(s % 60)}`
})

/* ───────── 단추 ───────── */
const modal = ref(null)

async function onCall() {
  try {
    const r = await callWorkers(place.value.processId, { alarmId: props.alarmId })
    toast(`${place.value.processName || '공정'} 담당자 ${r.sentCount}명에게 호출을 보냈습니다.`)
  } catch (error) {
    toast(error.message, { type: 'err' })
  }
}

/** 창을 접고 그 공정 배치도로 (상단 바 빨간 띠를 누르면 다시 열림) */
function onMap() {
  alert.close()
  const { siteId, factoryId, processId } = place.value
  if (siteId && factoryId && processId) router.push({ name: 'process', params: { siteId, factoryId, processId } })
  toast('상단의 빨간 띠를 누르면 이 창이 다시 열립니다.', { type: 'info' })
}

/** '화면 보기': 창을 접음 (상단 바 빨간 띠로 다시 열 수 있음) */
function onCollapse() {
  alert.close()
  toast('상단의 빨간 띠를 누르면 이 창이 다시 열립니다.', { type: 'info' })
}

function onClosed() {
  alert.close()
  clearToasts()
  toast(`경보를 종료했습니다. ${stoppedName.value || '설비'}는 현장 점검 후 다시 가동할 수 있습니다.`)
}

function onCleared() {
  alert.close()
  clearToasts()
  toast('화재 경보와 경고 방송을 해제했습니다.')
}

/** 비밀번호가 틀리면 창 흔들기 (BaseModal 의 잠금 흔들기와 같은 방법) */
function shake() {
  modal.value?.$el?.querySelector('.box')?.classList.remove('shake')
  void modal.value?.$el?.offsetWidth
  modal.value?.$el?.querySelector('.box')?.classList.add('shake')
}
</script>

<template>
  <BaseModal ref="modal" :width="isFire ? 1160 : 1240" lock tone="alarm" :red-dim="isFire" :label="isFire ? '화재 발생' : '경보 발생'">
    <p v-if="errorMessage" class="state">{{ errorMessage }}</p>
    <p v-else-if="!response" class="state">불러오는 중</p>

    <template v-else>
      <AlarmHead
        :response="response"
        :fire="isFire"
        :stopped="stopped"
        :stopped-name="stoppedName"
        :location="location"
        :hazard-label="hazardLabel"
        :elapsed="elapsed"
        @collapse="onCollapse"
      />

      <!-- 화재 -->
      <template v-if="isFire">
        <div class="pad">
          <BroadcastBand :broadcast="fire.broadcast" :elapsed-sec="elapsedSec" />
        </div>
        <div class="fire-cols">
          <ThermalImage
            :image-url="fire.thermal.imageUrl"
            :max="fire.thermal.max"
            :max-point="fire.thermal.maxPoint"
            :label-offset="{ x: -150, y: -52 }"
            :scale="fire.thermal.scale"
            :zone-name="zoneOf(fire.thermal)"
            class="ir fire-ir"
          />
          <div>
            <div class="vals">
              <div class="m-box"><small>열화상 최고</small><b class="mono">{{ fire.values.thermalMax }}<i>℃</i></b></div>
              <div class="m-box"><small>온도 {{ tagOf('TEMP') }}</small><b class="mono">{{ fire.values.temp }}<i>℃</i></b></div>
              <div class="m-box"><small>CO {{ tagOf('CO') }}</small><b class="mono">{{ fire.values.co }}<i>ppm</i></b></div>
            </div>
            <p class="basis">{{ fire.basis }}</p>
            <div class="m-sect top"><AppIcon name="shield" :size="15" />자동 조치</div>
            <ProblemLog :actions="fire.actions" class="gap" />
          </div>
        </div>
        <FireRelease :fire="fire" :can-act="alert.canAct" @wrong="shake" @cleared="onCleared" />
      </template>

      <!-- 경보 -->
      <template v-else>
        <div class="alarm-cols">
          <section>
            <div class="m-sect"><AppIcon name="cam" :size="15" />열화상 카메라</div>
            <ThermalImage
              v-if="response.thermal?.imageUrl"
              :image-url="response.thermal.imageUrl"
              :max="response.thermal.max"
              :max-point="response.thermal.maxPoint"
              :label-offset="{ x: -150, y: 32 }"
              :scale="response.thermal.scale"
              :zone-name="zoneOf(response.thermal)"
              class="gap ir"
            />
            <!-- 응답에 열화상이 없을 때 (창은 그대로 열림) -->
            <div v-else class="gap ir no-ir"><AppIcon name="cam" :size="22" />열화상 이미지를 받지 못했습니다</div>
            <div class="m-sect top"><AppIcon name="info" :size="15" />경보 사유</div>
            <div class="m-box reason gap">
              <b>{{ response.reason }}</b>
              <small>{{ response.reasonDetail }}</small>
            </div>
            <!-- 가스 경보: 배기팬 자동 제어 결과 (자동이면 100%로 올림, 수동이면 승인 대기 · 승인 · 거절 버튼) — 셧다운으로 설비를 세운 뒤에는 숨김 -->
            <FanAutoNotice v-if="response.fanAuto && !stopped" :fan="response.fanAuto" tone="alarm" class="gap" />
          </section>
          <section>
            <div class="m-sect"><AppIcon name="chart" :size="15" />센서 데이터</div>
            <EmergencySensors :sensors="response.sensors" class="gap" />
          </section>
          <section class="log">
            <div class="m-sect"><AppIcon name="file" :size="15" />문제 로그</div>
            <ProblemLog :logs="response.logs" class="gap fill" />
          </section>
        </div>
        <ShutdownPanel
          :response="response"
          :can-act="alert.canAct"
          :process-id="place.processId"
          :process-name="place.processName"
          @call="onCall" @map="onMap" @closed="onClosed" />
      </template>
    </template>
  </BaseModal>
</template>

<style scoped>
.state {
  padding: 60px 28px;
  text-align: center;
  color: var(--color-text-sub);
}

.gap {
  margin-top: 8px;
}

.m-sect.top {
  margin-top: 14px;
}

/* 경보: 440 | 380 | 나머지 (설계안) */
.alarm-cols {
  display: grid;
  grid-template-columns: 440px 380px minmax(0, 1fr);
  gap: 28px;
  padding: 20px 28px;
}

.log {
  display: flex;
  flex-direction: column;
}

.fill {
  flex: 1;
  max-height: 470px;
}

.reason {
  padding: 14px 16px;
  border-color: #fecaca;
  background: #fef2f2;
  font-size: 14px;
  line-height: 22px;
}

.reason small {
  display: block;
  margin-top: 4px;
  font-size: 13px;
  color: #7f1d1d;
}

/* 화재: 520 | 나머지 */
.pad {
  padding: 18px 28px 0;
}

.fire-cols {
  display: grid;
  grid-template-columns: 520px minmax(0, 1fr);
  gap: 24px;
  padding: 18px 28px 22px;
}

.fire-ir {
  border: 2px solid var(--color-level-alarm);
}

.vals {
  display: flex;
  gap: 10px;
}

.vals > div {
  flex: 1;
  padding: 12px 14px;
  border-color: var(--color-level-alarm);
}

.vals small {
  font-size: 12px;
  color: #64748b;
}

.vals b {
  display: block;
  margin-top: 2px;
  font-size: 26px;
  font-weight: 600;
  color: var(--color-level-alarm-text);
}

.vals i {
  margin-left: 3px;
  font-family: var(--font-sans);
  font-size: 13px;
  font-style: normal;
  color: var(--color-text-weak);
}

.basis {
  margin-top: 10px;
  font-size: 12.5px;
  line-height: 19px;
  color: #7f1d1d;
}
.no-ir {
  aspect-ratio: 4 / 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface-sub);
  font-size: 13px;
  color: var(--color-text-weak);
}
</style>
