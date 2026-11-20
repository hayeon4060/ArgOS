<!--
  경보 창 아래 '설비 셧다운' (화면설계안 mAlarm() #sd · shutdownSeq(), 화면설계서 C4 ⑦⑧)
  - 고르기: 경보가 난 설비(기본) / 같은 공정의 다른 설비 / '(공정 이름) 전체'
  - 기본 선택: 응답 equipment 중 단계가 정상이 아닌 첫 설비, 없으면 첫 설비 (설비 번호를 고정하지 않음)
  - 2초 길게 누르기 → 셧다운 명령. 공정 전체는 한 번 더 확인
    확인을 취소하거나 요청이 실패하면 길게 누르기 단추를 처음 상태로 되돌려 다시 누를 수 있게 함
    POST /equipment/{id}/shutdown 또는 POST /processes/{id}/shutdown (접수만, 진행은 SSE command.result)
  - 진행: 진행 막대 + 3단계(버너 연료 차단 → 컨베이어 정지 → 배기팬 추가 가동 후 정지)가 차례로 채워짐
  - 끝나면 '셧다운 완료' → 조치 결과 한 줄 + '경보 종료' (모든 센서가 경보 기준 아래일 때만 켜짐)
  - 공정 담당자 호출, 설비 배치도에서 보기
  - 본사 관리자: 셧다운·경보 종료 단추 없음 (열람만)
-->
<script setup>
import { computed, ref } from 'vue'
import { closeAlarm, shutdownEquipment, shutdownProcess } from '@/api/emergency'
import AppIcon from '@/components/AppIcon.vue'
import HoldButton from '@/components/HoldButton.vue'
import { toast } from '@/utils/toast'

const props = defineProps({
  /** GET /alarms/{id}/response */
  response: { type: Object, required: true },
  canAct: { type: Boolean, default: false },
  /** 이 알림의 공정 (응답에 없어서 창이 알림 목록 줄에서 꺼내 줌) */
  processId: { type: [Number, String], default: null },
  processName: { type: String, default: '' },
})

const emit = defineEmits(['call', 'map', 'closed'])

const ALL = 'ALL'
const equipment = computed(() => props.response.equipment)
// 경보가 난 설비(단계가 정상이 아닌 설비)를 맨 앞에
const ordered = computed(() => [...equipment.value].sort((a, b) => (b.level !== 'NORMAL') - (a.level !== 'NORMAL')))
// 기본 선택: 정상이 아닌 첫 설비 → 없으면 첫 설비 (창을 열 때 한 번만 정함, 다시 불러와도 고른 것은 그대로)
const target = ref(equipment.value.find((e) => e.level !== 'NORMAL')?.id ?? equipment.value[0]?.id ?? ALL)
const allLabel = computed(() => `${props.processName || '공정'} 전체`)
const targetName = computed(() => (target.value === ALL ? allLabel.value : (equipment.value.find((e) => e.id === target.value)?.name ?? '')))

const sd = computed(() => props.response.shutdown)
const running = computed(() => sd.value && !sd.value.auto)
const doneCount = computed(() => sd.value?.steps.filter((s) => s.at).length ?? 0)
const pct = computed(() => Math.round((doneCount.value / 3) * 100))

const heldMs = ref(0)
const confirmAll = ref(false)
const sending = ref(false)
const hold = ref(null) // 길게 누르기 단추 (reset() 을 부르려고)

/** 길게 누르기 단추를 처음 상태로 (다시 누를 수 있게) */
function resetHold() {
  hold.value?.reset()
}

/** 공정 전체 확인에서 '취소' */
function cancelAll() {
  confirmAll.value = false
  resetHold()
}

/** 2초 다 누르면: 설비 하나는 바로, 공정 전체는 확인 한 번 더 */
function onHoldDone() {
  if (target.value === ALL) confirmAll.value = true
  else send()
}

async function send() {
  confirmAll.value = false
  sending.value = true
  try {
    const body = { alarmId: props.response.id, reason: props.response.reason }
    if (target.value === ALL) await shutdownProcess(props.processId, { ...body, equipmentIds: [] })
    else await shutdownEquipment(target.value, body)
  } catch (error) {
    toast(error.message, { type: 'err' })
    resetHold() // 실패하면 다시 누를 수 있게
  } finally {
    sending.value = false
  }
}

/* ───────── 경보 종료 ───────── */
const result = ref('')
const closing = ref(false)

async function onClose() {
  closing.value = true
  try {
    await closeAlarm(props.response.id, { result: result.value || '설비 셧다운 후 기준 아래로 복귀', followUp: '' })
    emit('closed')
  } catch (error) {
    toast(error.message, { type: 'err' })
  } finally {
    closing.value = false
  }
}

const hm = (iso) => iso?.slice(11, 19) ?? ''
</script>

<template>
  <section class="sd">
    <!-- 셧다운 전: 고르기 + 길게 누르기 -->
    <template v-if="!running">
      <div class="left">
        <div class="h">설비 셧다운</div>
        <p class="desc">
          버너 연료 차단 → 컨베이어 정지 → 배기팬 5분 추가 가동 후 정지 순서로 진행됩니다.<br />
          셧다운 후에는 현장 점검을 마쳐야 다시 가동할 수 있습니다.
        </p>
        <div class="targets" role="radiogroup" aria-label="셧다운할 설비">
          <label v-for="e in ordered" :key="e.id" :class="{ on: target === e.id, hot: e.level !== 'NORMAL', off: e.runState !== 'RUNNING' }">
            <input v-model="target" type="radio" :value="e.id" :disabled="!canAct || e.runState !== 'RUNNING'" />
            {{ e.name }}<small v-if="e.runState !== 'RUNNING'">정지됨</small>
          </label>
          <label :class="{ on: target === ALL, all: true }">
            <input v-model="target" type="radio" :value="ALL" :disabled="!canAct || !processId" />{{ allLabel }}
          </label>
        </div>
        <div class="m-actions links">
          <button type="button" class="btn" @click="emit('call')"><AppIcon name="phone" :size="16" />공정 담당자 호출</button>
          <button type="button" class="btn" @click="emit('map')"><AppIcon name="layers" :size="16" />설비 배치도에서 보기</button>
        </div>
      </div>

      <div v-if="canAct" class="right">
        <div class="hint">
          <b>{{ targetName }} 셧다운</b>
          <span class="mono">{{ heldMs > 0 ? `${(heldMs / 1000).toFixed(1)} / 2.0초` : '버튼을 2초간 길게 누르세요' }}</span>
        </div>
        <HoldButton ref="hold" :size="132" color="#DC2626" label="셧다운" icon="power" :disabled="sending" @progress="heldMs = $event" @done="onHoldDone" />
        <!-- 공정 전체: 한 번 더 확인 (화면설계서 C4) -->
        <div v-if="confirmAll" class="confirm pop-in">
          {{ processName || '이 공정' }} 설비 {{ equipment.length }}대가 모두 정지됩니다.
          <div class="m-actions">
            <button type="button" class="btn" @click="cancelAll">취소</button>
            <button type="button" class="btn danger" @click="send">셧다운</button>
          </div>
        </div>
      </div>
      <p v-else class="readonly">본사 관리자는 셧다운하지 않습니다. 현장 공장 담당자가 조치합니다.</p>
    </template>

    <!-- 셧다운 진행 · 완료 -->
    <template v-else>
      <div class="prog">
        <div class="ph">
          <b :class="{ ok: sd.done }">{{ sd.done ? '셧다운 완료' : '셧다운 진행 중' }}</b>
          <span class="mono">{{ pct }}%</span>
          <div class="pbar"><i :class="{ ok: sd.done }" :style="{ width: `${pct}%` }" /></div>
        </div>
        <div class="body">
          <div class="stepper">
            <div v-for="(s, i) in sd.steps" :key="s.label" class="sp" :class="{ dn: s.at, run: !s.at && i === doneCount }">
              <i><AppIcon v-if="s.at" name="ok" :size="13" /></i>{{ s.label }}<span class="mono">{{ hm(s.at) }}</span>
            </div>
          </div>
          <div class="after">
            <template v-if="!sd.done">진행 중에는 창을 닫을 수 없습니다.<br />배기팬은 남은 가스를 빼낸 뒤 자동으로 멈춥니다.</template>
            <template v-else-if="canAct">
              현장 점검을 마친 뒤 설비 상세 화면에서 다시 가동할 수 있습니다.
              <div class="close-row">
                <input v-model="result" type="text" maxlength="80" placeholder="조치 결과 한 줄 (선택)" aria-label="조치 결과" />
                <button
                  type="button"
                  class="btn btn-primary"
                  :disabled="response.valueStillOver || closing"
                  :title="response.valueStillOver ? '아직 경보 기준 위인 센서가 있습니다' : ''"
                  @click="onClose"
                >
                  경보 종료
                </button>
              </div>
            </template>
            <template v-else>현장 공장 담당자가 경보를 종료합니다.</template>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.sd {
  display: flex;
  align-items: center;
  gap: 24px;
  min-height: 176px;
  padding: 18px 28px;
  border-top: 1px solid var(--color-border);
  background: #f8fafc;
}

.left {
  flex: 1;
  min-width: 0;
}

.h {
  font-size: 16px;
  font-weight: 700;
}

.desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 21px;
  color: var(--color-text-sub);
}

/* 설비 고르기 (작은 단추 모양) */
.targets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.targets label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 12.5px;
  cursor: pointer;
}

.targets input {
  display: none;
}

.targets label.hot {
  border-color: var(--color-level-alarm);
  color: var(--color-level-alarm-text);
  font-weight: 700;
}

.targets label.on {
  border-color: var(--color-level-alarm);
  background: var(--color-level-alarm);
  color: #fff;
}

.targets label.all {
  border-style: dashed;
}

.targets label.off {
  opacity: 0.5;
  cursor: not-allowed;
}

.targets small {
  font-size: 11px;
}

.links {
  margin-top: 12px;
}

.links .btn {
  height: 38px;
  font-size: 13px;
}

.right {
  position: relative;
  display: flex;
  align-items: center;
  gap: 18px;
}

.hint {
  text-align: right;
}

.hint b {
  display: block;
  font-size: 15px;
  color: var(--color-level-alarm-text);
}

.hint span {
  display: block;
  margin-top: 4px;
  font-size: 12.5px;
  color: #64748b;
}

.confirm {
  position: absolute;
  right: 0;
  bottom: calc(100% + 8px);
  width: 300px;
  padding: 14px;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  background: #fff;
  box-shadow: 0 12px 32px rgba(127, 29, 29, 0.2);
  font-size: 13px;
}

.confirm .m-actions {
  margin-top: 10px;
}

.confirm .btn {
  flex: 1;
  height: 36px;
}

.btn.danger {
  border-color: var(--color-level-alarm);
  background: var(--color-level-alarm);
  color: #fff;
  font-weight: 700;
}

.readonly {
  width: 300px;
  font-size: 13px;
  color: var(--color-text-sub);
}

/* 진행 */
.prog {
  flex: 1;
}

.ph {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ph b {
  font-size: 16px;
}

.ph b.ok {
  color: var(--color-level-normal-text);
}

.ph .mono {
  font-size: 13px;
  color: #64748b;
}

.pbar {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: var(--color-border);
  overflow: hidden;
}

.pbar i {
  display: block;
  height: 100%;
  background: var(--color-level-alarm);
  transition:
    width 400ms linear,
    background 300ms;
}

.pbar i.ok {
  background: var(--color-level-normal);
}

.body {
  display: flex;
  gap: 40px;
  margin-top: 16px;
}

.stepper {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sp {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: var(--color-text-weak);
  transition: color 300ms;
}

.sp i {
  width: 24px;
  height: 24px;
  flex: none;
  display: grid;
  place-items: center;
  border: 2px solid var(--color-border-strong);
  border-radius: 50%;
  color: #fff;
  transition:
    background 300ms,
    border-color 300ms;
}

.sp i .app-icon {
  stroke-width: 3.4;
}

/* 진행 중인 단계: 빨간 고리가 돎 */
.sp.run {
  color: var(--color-text);
  font-weight: 600;
}

.sp.run i {
  border-color: var(--color-level-alarm);
  border-top-color: transparent;
  animation: spin 0.8s linear infinite; /* base.css */
}

.sp.dn {
  color: var(--color-text);
}

.sp.dn i {
  border-color: var(--color-level-normal);
  background: var(--color-level-normal);
}

.sp .mono {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-weak);
}

.after {
  width: 380px;
  font-size: 13px;
  line-height: 21px;
  color: var(--color-text-sub);
}

.close-row {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.close-row input {
  flex: 1;
  height: 42px;
  padding: 0 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.close-row .btn {
  height: 42px;
  padding: 0 18px;
}
</style>
