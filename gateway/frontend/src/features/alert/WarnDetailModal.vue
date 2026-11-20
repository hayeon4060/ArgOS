<!--
  주의 상세 · 체크리스트 전송 창 (화면설계안 js/v-modals.js mWarnDetail(), 화면설계서 C2)
  - 2026-11-19 재원님 요청: '센서 데이터 · 열화상 확인' 창을 이 창에 합침 (따로 여는 단추·창 삭제, 겹치던 추이 그래프는 하나만)
    제목에 유형을 붙임: CO 가 높으면 '가스 주의 상세', 전류·온도가 높으면 '화재 주의 상세'
  - 왼쪽: 발생 원인(문장·기준 띠) / 배기팬 자동 가동 줄(가스 주의만, detail.fanAuto) / 예상 원인 / 센서 데이터(센서 3종 값·기준·단계)
  - 가운데: 열화상 이미지(주의 발생 시점 1장) + 최고 온도 위치 · 최고/평균 · 평소 같은 위치
  - 아래(왼쪽·가운데 밑): 센서 추이 2개 (최근 40분) — 2026-11-19 재원님 요청
    화재 주의 = 온도 · 전류 / 가스 주의 = CO · 전류. 응답에 trends 가 없으면 알림 센서 추이 하나만
  - 오른쪽: 조치 체크리스트(빼기·추가) / 전송 대상(공정 담당자) / 닫기 · 공정 담당자 앱으로 전송
  - 화면 너비 1500px 이하: 두 칸 (왼쪽·가운데·추이가 한 칸에 위아래로)
  - 데이터: GET /alarms/{id}/detail, GET /checklist-templates, GET /processes/{id}/workers
  - 전송: POST /checklists → '보내는 중' → 알림 띠 → 창 닫힘 (상단 바에 체크리스트 진행이 나타남)
  - 이미 보낸 알림이면 항목을 고칠 수 없고 단추는 '전송 완료'
-->
<script setup>
import { computed, onMounted, ref } from 'vue'
import { placeOf, sensorTypeOfDetail, trendsOf } from './alarmPlace'
import { hazardLabelOf } from './alarmView'
import { hazardOf } from '@/features/history/historyFilter'
import ChecklistEditor from './ChecklistEditor.vue'
import FanAutoNotice from './FanAutoNotice.vue'
import MiniTrend from './MiniTrend.vue'
import WarnCause from './WarnCause.vue'
import WarnSensorRows from './WarnSensorRows.vue'
import WorkerPicker from './WorkerPicker.vue'
import { getAlarmDetail } from '@/api/alarms'
import { getChecklistTemplate, getProcessWorkers, sendChecklist } from '@/api/checklists'
import AppIcon from '@/components/AppIcon.vue'
import BaseModal from '@/components/BaseModal.vue'
import ThermalImage from '@/features/equipment/ThermalImage.vue'
import { useAlertStore } from '@/stores/alert'
import { useAuthStore } from '@/stores/auth'
import { toLayoutPx } from '@/utils/screenScale'
import { toast } from '@/utils/toast'

const props = defineProps({
  alarmId: { type: Number, required: true },
})

const alert = useAlertStore()
const auth = useAuthStore()

const detail = ref(null)
const items = ref([])
const templateId = ref(null)
const workers = ref([])
const assigneeId = ref(null)
const errorMessage = ref('')

onMounted(async () => {
  try {
    const d = await getAlarmDetail(props.alarmId)
    const [template, list] = await Promise.all([
      // 센서 종류: 백엔드 detail 에는 sensorType 이 없어 알림 센서에서 찾음 (전류로 고정하지 않음)
      getChecklistTemplate({ equipmentType: d.equipmentType, sensorType: sensorTypeOfDetail(d) }),
      getProcessWorkers(d.processId),
    ])
    detail.value = d
    items.value = [...template.items]
    templateId.value = template.templateId
    workers.value = list
    // 기본 선택: 근무 중인 첫 번째 담당자 (화면설계서: 근무 중이고 진행 건이 가장 적은 사람 → 진행 건 수는 API 에 없음)
    assigneeId.value = list.find((w) => w.onDuty)?.id ?? list[0]?.id ?? null
  } catch (error) {
    errorMessage.value = error.message
  }
})

/** 유형 'FIRE' · 'GAS' (응답의 hazard, 없으면 알림 센서 종류로) */
const hazard = computed(() => {
  const row = { hazard: detail.value?.hazard, sensorType: sensorTypeOfDetail(detail.value) }
  return row.hazard || row.sensorType ? hazardOf(row) : null
})

/** 아래 추이 그래프 (화재: 온도·전류 / 가스: CO·전류) */
const trends = computed(() => trendsOf(detail.value, hazard.value))

/** 유형 문구 '가스 주의' · '화재 주의' (응답의 hazard, 없으면 알림 센서 종류로) */
const kind = computed(() => {
  const label = hazardLabelOf({ hazard: detail.value?.hazard, sensorType: sensorTypeOfDetail(detail.value) })
  return [label, '주의'].filter(Boolean).join(' ')
})

/* 열화상: 발생 시각 · 사진 아래 이름 'IR-01 · 도장 공정' */
const time = computed(() => detail.value?.raisedAt.slice(11, 19) ?? '')
const zoneName = computed(() => [detail.value?.thermal?.cameraTag, placeOf(detail.value).processName].filter(Boolean).join(' · '))

// 창 너비: 세 칸이 들어가게 넓게, 화면이 좁으면 화면에 맞춤
const modalWidth = Math.min(1640, toLayoutPx(window.innerWidth) - 48) // 배율이 걸린 화면에서는 화면 폭도 배율로 나눠서

/** 이미 보낸 알림 (조치 중) */
const sent = () => detail.value?.status === 'ACTING'

const sending = ref(false)

async function send() {
  sending.value = true
  try {
    await sendChecklist({
      alarmId: props.alarmId,
      templateId: templateId.value,
      assigneeId: assigneeId.value,
      items: items.value,
      sentBy: auth.user?.name,
    })
    const name = workers.value.find((w) => w.id === assigneeId.value)?.name ?? '담당자'
    alert.close()
    toast(`${name} 님 앱으로 체크리스트를 보냈습니다.`)
  } catch (error) {
    // 실패하면 창을 닫지 않고 알림 띠로 (화면설계서 C2 '전송 실패')
    toast(error.message || '전송하지 못했습니다. 다시 시도하세요.', { type: 'err' })
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <BaseModal :width="modalWidth" label="주의 상세" @close="alert.close()">
    <template #default="{ close }">
      <div class="m-head">
        <span class="m-chip warn big"><AppIcon name="tri" :size="15" />2단계 {{ kind }}</span>
        <h2>{{ kind }} 상세</h2>
        <span class="loc">{{ detail?.location }}</span>
        <button type="button" class="m-close" aria-label="닫기" @click="close"><AppIcon name="x" /></button>
      </div>

      <p v-if="errorMessage" class="state">{{ errorMessage }}</p>
      <p v-else-if="!detail" class="state">불러오는 중</p>

      <div v-else class="cols">
        <!-- 왼쪽: 원인 · 예상 원인 · 센서 3종 -->
        <section class="left">
          <div class="m-sect"><AppIcon name="info" :size="15" />발생 원인</div>
          <WarnCause :detail="detail" class="gap" />
          <!-- 가스 주의: 배기팬 자동 제어 결과 + 속도 직접 조절 20 ~ 60 Hz -->
          <FanAutoNotice v-if="detail.fanAuto" :fan="detail.fanAuto" tone="warn" adjustable class="gap-s" />

          <div class="m-sect top"><AppIcon name="gear" :size="15" />예상 원인</div>
          <div class="causes">
            <span v-for="c in detail.causeCandidates" :key="c" class="m-chip gray cause">{{ c }}</span>
          </div>

          <div class="m-sect top"><AppIcon name="chart" :size="15" />센서 데이터 <small>{{ time }} 기준</small></div>
          <WarnSensorRows :sensors="detail.sensors" class="gap" />
        </section>

        <!-- 가운데: 열화상 1장 · 알림 센서 추이 -->
        <section class="mid">
          <template v-if="detail.thermal">
            <div class="m-sect"><AppIcon name="cam" :size="15" />열화상 이미지 <small>주의 발생 시점 1장</small></div>
            <ThermalImage
              class="gap img"
              :image-url="detail.thermal.imageUrl"
              :max="detail.thermal.max"
              :max-point="detail.thermal.maxPoint"
              max-label="최고 온도"
              :label-offset="{ x: -160, y: 36 }"
              :live="false"
              :zone-name="zoneName"
              :time="time"
            />
            <div class="facts">
              <div class="m-box"><small>최고 온도 위치</small><b>{{ detail.thermal.zoneName }}</b></div>
              <div class="m-box"><small>최고 / 평균</small><b class="mono">{{ detail.thermal.max.toFixed(1) }} / {{ detail.thermal.avg.toFixed(1) }} ℃</b></div>
              <div class="m-box"><small>평소 같은 위치</small><b class="mono">{{ detail.thermal.usualAtMax.toFixed(1) }} ℃</b></div>
            </div>
          </template>
        </section>

        <!-- 아래: 센서 추이 2개 (화재 = 온도·전류, 가스 = CO·전류) -->
        <section v-if="trends.length" class="trends">
          <MiniTrend
            v-for="t in trends"
            :key="t.tag"
            :trend="t.trend"
            :title="t.title"
            :dec="t.dec"
            :sub="`${t.tag} · 최근 40분`"
            :height="180"
          />
        </section>

        <!-- 오른쪽: 체크리스트 · 받는 사람 · 전송 -->
        <section class="right">
          <div class="m-sect"><AppIcon name="clip" :size="15" />조치 체크리스트 <small>{{ items.length }}개 항목</small></div>
          <ChecklistEditor v-model="items" :readonly="sent()" class="gap" />

          <div class="m-sect top"><AppIcon name="phone" :size="15" />전송 대상</div>
          <WorkerPicker v-model="assigneeId" :workers="workers" :process-name="placeOf(detail).processName" class="gap" />

          <p class="note">
            체크리스트와 센서 값, 열화상 이미지가 함께 전송됩니다.<br />
            담당자가 항목을 모두 완료하면 대시보드 상단에서 확인할 수 있습니다.
          </p>
          <div class="m-actions buttons">
            <button type="button" class="btn close" @click="close">닫기</button>
            <button
              type="button"
              class="btn btn-primary send"
              :disabled="sent() || sending || !assigneeId || items.length === 0"
              @click="send"
            >
              <template v-if="sent()"><AppIcon name="check" :size="17" />전송 완료</template>
              <template v-else-if="sending"><span class="m-spin" />보내는 중</template>
              <template v-else><AppIcon name="send" :size="17" />공정 담당자 앱으로 전송</template>
            </button>
          </div>
        </section>
      </div>
    </template>
  </BaseModal>
</template>

<style scoped>
.state {
  padding: 60px 28px;
  text-align: center;
  color: var(--color-text-sub);
}

/* 세 칸: 왼쪽(원인·센서) | 가운데(열화상) | 오른쪽(체크리스트·전송), 왼쪽·가운데 밑에 추이 2개 */
.cols {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  grid-template-areas:
    'left mid right'
    'trends trends right';
  gap: 16px 28px;
  padding: 20px 28px 28px;
}

.left {
  grid-area: left;
}

.mid {
  grid-area: mid;
}

.right {
  grid-area: right;
}

/* 추이 2개: 왼쪽·가운데 칸 밑에 하나씩 나란히 (칸 간격과 같은 28px) */
.trends {
  grid-area: trends;
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(0, 1fr);
  gap: 28px;
}

/* 좁은 화면: 두 칸 (왼쪽 아래에 열화상·추이, 추이는 위아래로) */
@media (max-width: 1500px) {
  .cols {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    grid-template-areas:
      'left right'
      'mid right'
      'trends right';
  }

  .trends {
    grid-auto-flow: row;
    gap: 12px;
  }
}

.img {
  /* 사진이 뜰 때 200ms 투명도 (화면설계서 C2) */
  animation: softin 200ms ease-out both;
}

/* 열화상 아래 세 칸 (최고 온도 위치 · 최고/평균 · 평소 같은 위치) */
.facts {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.facts > div {
  flex: 1;
  min-width: 0;
  padding: 9px 12px;
}

.facts small {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.facts b {
  font-size: 13.5px;
  white-space: nowrap;
}

.gap {
  margin-top: 10px;
}

.gap-s {
  margin-top: 12px;
}

.m-sect.top {
  margin-top: 18px;
}

.causes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.cause {
  padding: 6px 12px;
  font-size: 13px;
  font-weight: 600;
}

.note {
  margin-top: 12px;
  font-size: 12.5px;
  line-height: 19px;
  color: #64748b;
}

.buttons {
  margin-top: 16px;
}

.close {
  width: 120px;
  height: 48px;
}

.send {
  flex: 1;
  height: 48px;
  font-size: 15px;
}
</style>
