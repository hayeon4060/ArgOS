<!--
  배기팬 속도 직접 조절 (2026-11-20 재원님 요청: 가스 주의 단계에서 20 ~ 60 Hz 만)
  - FanAutoNotice(배기팬 자동 가동 줄) 아래에 붙음 → 주의 알림 창 · 주의 상세 창만 (경보 대응 창은 아님)
  - 슬라이더 · − · + 로 Hz 를 고르고 '적용' → POST /equipment/{id}/fan { hz } (이미 있는 API, api/equipment.js setFanHz)
  - 화면은 Hz 를 크게, rpm(Hz × 30)을 옆에 작게
  - 자동 모드여도 바꿀 수 있음: 자동으로 올린 값 대신 직접 정한 값으로 바뀐다고 안내
  - 공장 담당자만 보임 (부모 FanAutoNotice 가 canDecide 로 거름, 서버도 본사 관리자는 403)
  - 보낸 값은 부모에게 'sent' 로 알림 → 부모가 fan.manualHz 로 다시 내려 줌 (위 줄 제목 · rpm 도 같은 값)
-->
<script setup>
import { computed, ref, watch } from 'vue'
import { setFanHz } from '@/api/equipment'
import { FAN_HZ, RPM_PER_HZ } from '@/types/codes'
import { toast } from '@/utils/toast'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps({
  /** 알림의 fanAuto { equipmentId, fanEquipmentId?, fanName, mode, rpm, beforeRpm, status, manualHz? } */
  fan: { type: Object, required: true },
})
const emit = defineEmits(['sent'])

/** 20 ~ 60 사이 정수로 맞춤 */
const clamp = (v) => Math.min(FAN_HZ.MAX, Math.max(FAN_HZ.MIN, Math.round(v)))
const rpmOf = (hz) => (hz * RPM_PER_HZ).toLocaleString()

/**
 * 지금 배기팬 값(Hz): 직접 정한 값(manualHz) → 자동으로 올린 값 → 평소 값
 * (자동 값은 반영됐거나 올리는 중일 때만, 승인 대기 · 거절이면 평소 값)
 */
const current = computed(() => {
  if (props.fan.manualHz != null) return clamp(props.fan.manualHz)
  const rpm = ['APPLIED', 'SENT'].includes(props.fan.status) ? props.fan.rpm : props.fan.beforeRpm
  return clamp((rpm ?? 900) / RPM_PER_HZ)
})

/** 슬라이더로 고르는 값 (지금 값이 바뀌면 따라감) */
const hz = ref(current.value)
watch(current, (v) => (hz.value = v))

const changed = computed(() => hz.value !== current.value)
const step = (d) => (hz.value = clamp(hz.value + d))

/* ───────── 적용 ───────── */
const busy = ref(false)
async function apply() {
  if (!changed.value || busy.value) return
  busy.value = true
  const target = hz.value
  try {
    // 실제 서버 응답은 배기팬 설비 id(fanEquipmentId)를 따로 줌. 예시 데이터는 없어서 건조로 id
    await setFanHz(props.fan.fanEquipmentId ?? props.fan.equipmentId, target)
    emit('sent', target)
    toast(`${props.fan.fanName} 속도를 ${target} Hz (${rpmOf(target)} rpm)로 바꾸라고 보냈습니다.`)
  } catch (error) {
    // 400 HZ_OUT_OF_RANGE · 409 COMMAND_PENDING(같은 명령 처리 중) · 403(본사 관리자)
    toast(error.message, { type: 'err' })
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="fan-speed">
    <div class="head">
      <span class="label"><AppIcon name="slider" :size="15" />속도 직접 조절</span>
      <span class="val mono">
        <b>{{ hz }}</b><i>Hz</i><small>{{ rpmOf(hz) }} rpm</small>
      </span>
    </div>

    <div class="row">
      <button type="button" class="pm" aria-label="1 Hz 내리기" :disabled="busy || hz <= FAN_HZ.MIN" @click="step(-1)">
        <AppIcon name="minus" :size="14" />
      </button>
      <input
        v-model.number="hz"
        type="range"
        class="range"
        :min="FAN_HZ.MIN"
        :max="FAN_HZ.MAX"
        step="1"
        :disabled="busy"
        aria-label="배기팬 주파수 (Hz)"
      />
      <button type="button" class="pm" aria-label="1 Hz 올리기" :disabled="busy || hz >= FAN_HZ.MAX" @click="step(1)">
        <AppIcon name="plus" :size="14" />
      </button>
      <button type="button" class="btn btn-primary apply" :disabled="!changed || busy" @click="apply">
        {{ busy ? '보내는 중' : '적용' }}
      </button>
    </div>

    <div class="scale mono">
      <span>{{ FAN_HZ.MIN }} Hz</span>
      <span>{{ FAN_HZ.MAX }} Hz</span>
    </div>

    <!-- 안내 한 줄: 보낸 뒤 / 자동 모드 / 그 밖 -->
    <small class="note">
      <template v-if="fan.manualHz != null">직접 정한 값 {{ current }} Hz로 운전합니다.</template>
      <template v-else-if="fan.mode === 'AUTO'">적용하면 자동으로 올린 값 대신 직접 정한 값으로 바뀝니다.</template>
      <template v-else>{{ FAN_HZ.MIN }} ~ {{ FAN_HZ.MAX }} Hz 사이에서 바로 바꿀 수 있습니다.</template>
    </small>
  </div>
</template>

<style scoped>
.fan-speed {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 10px;
  border-top: 1px dashed rgb(249 115 22 / 0.35);
}

.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text);
}

.val {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
}

.val b {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-level-warn-text);
}

.val i {
  font-style: normal;
  font-size: 12px;
  color: var(--color-text-sub);
}

.val small {
  margin-left: 6px;
  font-size: 12px;
  color: var(--color-text-weak);
}

.row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.range {
  flex: 1;
  min-width: 0;
  accent-color: var(--color-level-warn);
  cursor: pointer;
}

.range:disabled {
  cursor: default;
}

/* − · + 작은 동그라미 단추 */
.pm {
  flex: none;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border-strong);
  border-radius: 50%;
  background: #fff;
  color: var(--color-text-sub);
  cursor: pointer;
}

.pm:disabled {
  opacity: 0.4;
  cursor: default;
}

.apply {
  flex: none;
  height: 32px;
  padding: 0 14px;
  font-size: 13px;
}

/* 슬라이더 양 끝 눈금 글자 (− · + 단추 폭만큼 안쪽으로) */
.scale {
  display: flex;
  justify-content: space-between;
  margin: -4px 102px 0 34px;
  font-size: 11px;
  color: var(--color-text-weak);
}

.note {
  font-size: 12px;
  line-height: 17px;
  color: var(--color-text-sub);
}
</style>
