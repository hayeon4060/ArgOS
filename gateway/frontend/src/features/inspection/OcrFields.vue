<!--
  인식 결과 (화면설계안 vInspect() fields())
  - 위: 인식한 항목 / 직접 고친 항목 / 확인 필요 (남은 수가 있으면 주황)
  - 가운데(스크롤): 작업 개요 → 가스 농도 측정(그래프 + 측정값) → 사전 안전조치(8개 항목) → 작업 종료 확인
    · 확인 필요 칸은 주황 테두리 + '확인' 단추 + 이유('글자가 흐립니다'), 값을 고치거나 '확인'을 누르면 풀림
    · 고친 칸은 파란 바탕, 입력 칸에 들어가면 원본의 그 상자가 진해짐 (focus)
    · Enter: 확인 필요 칸이면 '확인', 아니면 입력 끝
    · 처음 결과가 나올 때 줄이 차례로 나타남 (70ms 간격)
  - 아래: 남은 확인 안내 + 다시 인식 / 임시 저장 / 검수 완료·저장 (확인 필요가 0일 때만 켜짐)
  - 인식 중에는 진행 막대와 빈 줄 모양, 인식 실패면 안내만
  - readOnly(본사 관리자·저장된 서류)면 고칠 수 없고 단추 대신 안내
-->
<script setup>
import { computed, nextTick, ref } from 'vue'
import { gasOption } from './inspectionCharts'
import { GROUPS, shortTime } from './inspectionMeta'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps({
  /** wait 인식 중 · fail 인식 실패 · review 원본 대조 · saved 저장 끝 · empty 서류 없음 */
  mode: { type: String, required: true },
  /** 인식 결과 칸 (useOcrDoc 의 fields) */
  fields: { type: Array, default: () => [] },
  /** 서류 양식 칸 정의 (GET /doc-forms 의 fields — group · type · unit · limit · note · time) */
  formFields: { type: Array, default: () => [] },
  progress: { type: Number, default: 0 },
  uploadPct: { type: Number, default: 0 },
  needCheckLeft: { type: Number, default: 0 },
  editedCount: { type: Number, default: 0 },
  readOnly: { type: Boolean, default: false },
  /** 본사 관리자 등 고칠 수 없는 이유 (readOnly 일 때 안내) */
  readOnlyNote: { type: String, default: '' },
  saving: { type: Boolean, default: false },
  /** 저장된 서류 '11-12 16:44 저장 · 오승민' */
  savedAt: { type: String, default: '' },
  savedBy: { type: String, default: '' },
  reveal: { type: Boolean, default: false },
})

const emit = defineEmits(['confirm', 'edit', 'focus', 'blur', 'save', 'reocr', 'new'])

/** 결과 칸 + 양식 정의 (key 로 맞춤) */
const rows = computed(() => {
  const def = Object.fromEntries(props.formFields.map((f) => [f.key, f]))
  return props.fields.map((f) => ({ ...def[f.key], ...f, label: f.label || def[f.key]?.label, open: f.needCheck && !f.checked && !f.edited }))
})
const byGroup = (g) => rows.value.filter((r) => (r.group ?? 'overview') === g)
const sections = computed(() => Object.keys(GROUPS).filter((g) => byGroup(g).length))

/* 가스 농도: 그래프 + 기준 */
const gasRows = computed(() => byGroup('gas'))
const gasLimit = computed(() => gasRows.value[0]?.limit ?? 30)
const gasOk = computed(() => gasRows.value.every((r) => Number(r.value) < gasLimit.value))
const gasChart = computed(() =>
  gasOption(
    gasRows.value.map((r) => ({ label: r.time ?? r.label, value: Number(r.value) || 0, open: r.open })),
    gasLimit.value,
  ),
)

/* 숫자처럼 보이는 값은 고정폭 글꼴 */
const isNum = (v) => /^[\dA-Z]/.test(String(v)) && !/공장/.test(String(v))
const total = computed(() => props.fields.length)
const locked = computed(() => props.readOnly || props.mode === 'saved')

/* 원본 상자를 누르면 그 칸으로 스크롤하고 입력 칸에 들어감 (화면이 부름) */
const scroller = ref(null)
async function focusField(key) {
  await nextTick()
  const input = scroller.value?.querySelector(`[data-key="${key}"] input`)
  if (!input) return
  input.scrollIntoView({ block: 'center', behavior: 'smooth' })
  input.focus({ preventScroll: true })
  input.select()
}
defineExpose({ focusField })

/* 아래로 더 있다는 안내 (끝까지 내리면 숨김) */
const atEnd = ref(false)
function onScroll(e) {
  const el = e.target
  atEnd.value = el.scrollTop + el.clientHeight >= el.scrollHeight - 8
}
const scrollMore = () => scroller.value?.scrollBy({ top: 260, behavior: 'smooth' })

function onEnter(r, e) {
  if (r.open) emit('confirm', r.key)
  else e.target.blur()
}
</script>

<template>
  <div class="fd">
    <!-- 요약 3칸 -->
    <div class="fsum">
      <div>인식한 항목<b class="mono" :class="{ nov: mode !== 'review' && mode !== 'saved' }">{{ mode === 'review' || mode === 'saved' ? total : '–' }}<small v-if="mode === 'review' || mode === 'saved'">개</small></b></div>
      <div>직접 고친 항목<b class="mono" :class="{ nov: mode !== 'review' && mode !== 'saved' }">{{ mode === 'review' || mode === 'saved' ? editedCount : '–' }}<small v-if="mode === 'review' || mode === 'saved'">개</small></b></div>
      <div :class="{ w: needCheckLeft && mode === 'review' }">
        확인 필요<b class="mono" :class="{ ok: !needCheckLeft && mode === 'review', nov: mode !== 'review' && mode !== 'saved' }">{{ mode === 'review' || mode === 'saved' ? needCheckLeft : '–' }}<small v-if="mode === 'review' || mode === 'saved'">개</small></b>
      </div>
    </div>

    <!-- 인식 중: 진행 막대 + 빈 줄 -->
    <template v-if="mode === 'wait'">
      <div class="sec"><AppIcon name="file" :size="16" />{{ uploadPct < 100 ? '파일을 올리는 중입니다' : '글자를 읽는 중입니다' }}<span class="mono pct">{{ uploadPct < 100 ? `${uploadPct}%` : `${progress}%` }}</span></div>
      <div class="pbar"><i :style="{ width: `${uploadPct < 100 ? uploadPct * 0.3 : 30 + progress * 0.7}%` }" /></div>
      <div class="sks">
        <div v-for="i in 9" :key="i" class="skr"><i class="sk a" /><i class="sk b" /><i class="sk c" /></div>
      </div>
      <p class="foot">서류 한 장에 10~20초쯤 걸립니다. 인식하는 동안 다른 서류를 더 올려도 됩니다.</p>
    </template>

    <!-- 인식 실패 -->
    <div v-else-if="mode === 'fail'" class="state err">
      <AppIcon name="wifioff" :size="28" />
      <b>인식 결과를 받지 못했습니다</b>
      <span>원본 화면에서 다시 인식을 눌러 주세요.</span>
    </div>

    <!-- 서류 없음 -->
    <div v-else-if="mode === 'empty'" class="state">
      <AppIcon name="file" :size="28" />
      <b>서류를 고르면 인식 결과가 여기에 나옵니다</b>
    </div>

    <!-- 인식 결과 -->
    <template v-else>
      <div ref="scroller" class="fsc" @scroll="onScroll">
        <template v-for="(g, gi) in sections" :key="g">
          <div class="sec" :class="{ line: gi }">
            <AppIcon :name="GROUPS[g].icon" :size="16" />{{ GROUPS[g].label }}
            <span v-if="g === 'overview'" class="n">{{ locked ? '저장된 값' : '값을 눌러 바로 수정' }}</span>
            <span v-if="g === 'gas'" class="n">CO · {{ gasRows.length }}회</span>
            <span v-if="g === 'gas'" class="m-chip right" :class="gasOk ? 'ok' : 'alarm'">
              <AppIcon :name="gasOk ? 'check' : 'alert'" :size="12" />{{ gasOk ? `${gasRows.length}회 모두 적합` : '기준 초과' }}
            </span>
            <span v-if="g === 'pre'" class="n">{{ byGroup('pre').length }}개 항목</span>
            <span v-if="g === 'pre'" class="m-chip ok right"><AppIcon name="check" :size="12" />모두 확인</span>
          </div>

          <!-- 가스 그래프 -->
          <VChart v-if="g === 'gas'" class="gas" :option="gasChart" autoresize />

          <!-- 사전 안전조치: 번호 · 내용 · 예 -->
          <div v-if="g === 'pre'" class="pre">
            <div v-for="(r, i) in byGroup('pre')" :key="r.key">
              <span class="no mono">{{ i + 1 }}</span>
              <span class="tx">{{ r.label }}<small v-if="r.note">{{ r.note }}</small></span>
              <em><AppIcon name="ok" :size="12" />{{ r.value }}</em>
            </div>
          </div>

          <!-- 입력 줄 -->
          <template v-else>
            <div
              v-for="(r, i) in byGroup(g)"
              :key="r.key"
              class="fr"
              :class="{ lo: r.open && !locked, fadein: reveal }"
              :style="reveal ? { animationDelay: `${200 + i * 70}ms` } : undefined"
              :data-key="r.key"
            >
              <label :for="`f-${r.key}`">{{ r.label }}</label>
              <span class="in" :class="{ edt: r.edited, num: isNum(r.value) }">
                <input
                  :id="`f-${r.key}`"
                  :value="r.value"
                  :readonly="locked"
                  :aria-describedby="r.open ? `w-${r.key}` : undefined"
                  @input="emit('edit', r.key, $event.target.value)"
                  @focus="emit('focus', r.key)"
                  @blur="emit('blur', r.key)"
                  @keydown.enter="onEnter(r, $event)"
                />
                <small v-if="r.unit" class="unit">{{ r.unit }}</small>
              </span>
              <button v-if="r.open && !locked" type="button" class="btn okb" title="원본과 같으면 누르세요" @click="emit('confirm', r.key)">
                <AppIcon name="check" :size="14" />확인
              </button>
              <span v-else-if="r.needCheck && (r.checked || r.edited)" class="cf"><AppIcon name="check" :size="14" />확인함</span>
              <span v-else class="cf-space" />
              <span v-if="r.open && r.reason && !locked" :id="`w-${r.key}`" class="why">{{ r.reason }}</span>
            </div>
          </template>
        </template>
      </div>
      <button v-show="!atEnd" type="button" class="fsh" @click="scrollMore"><AppIcon name="cd" :size="14" />아래로 내리면 더 있습니다</button>

      <!-- 아래: 안내 + 단추 -->
      <template v-if="mode === 'saved'">
        <p class="hint ok"><AppIcon name="check" :size="15" />저장했습니다. <span class="mono weak">{{ shortTime(savedAt) }} 저장{{ savedBy ? ` · ${savedBy}` : '' }}</span></p>
        <div class="acts">
          <button v-if="!readOnly" type="button" class="btn right" @click="emit('new')"><AppIcon name="upload" :size="16" />다른 서류 올리기</button>
        </div>
      </template>
      <template v-else-if="readOnly">
        <p class="hint weak"><AppIcon name="info" :size="15" />{{ readOnlyNote }}</p>
      </template>
      <template v-else>
        <p class="hint" :class="{ ok: !needCheckLeft }">
          <AppIcon :name="needCheckLeft ? 'info' : 'check'" :size="15" />
          {{ needCheckLeft ? `확인 필요 ${needCheckLeft}개를 확인하면 저장할 수 있습니다.` : '모두 확인했습니다. 저장할 수 있습니다.' }}
        </p>
        <div class="acts">
          <button type="button" class="btn" :disabled="saving" @click="emit('reocr')"><AppIcon name="redo" :size="16" />다시 인식</button>
          <button type="button" class="btn" :disabled="saving" @click="emit('save', 'REVIEW')">임시 저장</button>
          <button
            type="button"
            class="btn btn-primary right save"
            :class="{ ready: !needCheckLeft }"
            :disabled="saving || needCheckLeft > 0"
            @click="emit('save', 'DONE')"
          >
            <span v-if="saving" class="m-spin" /><AppIcon v-else name="check" :size="16" />{{ saving ? '저장 중' : '검수 완료 · 저장' }}
          </button>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.fd {
  position: relative;
  height: 100%;
  min-width: 0;
}

.fsum {
  height: 72px;
  display: flex;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.fsum > div {
  flex: 1;
  padding: 11px 14px;
  border-right: 1px solid var(--color-border);
  font-size: 12.5px;
  color: var(--color-text-sub);
  transition: background 300ms;
}

.fsum > div:last-child {
  border: none;
}

.fsum b {
  display: block;
  margin-top: 3px;
  font-size: 22px;
  font-weight: 600;
  color: var(--color-text);
}

.fsum b small {
  margin-left: 3px;
  font-family: var(--font-sans, inherit);
  font-size: 12.5px;
  font-weight: 500;
  color: var(--color-text-weak);
}

.fsum .nov {
  color: #cbd5e1;
}

.fsum .w {
  border-radius: 0 5px 5px 0;
  background: #fff7ed;
}

.fsum .w b {
  color: var(--color-level-warn-text);
}

.fsum b.ok {
  color: var(--color-level-normal-text);
}

.sec {
  height: 44px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 14.5px;
  font-weight: 700;
}

.sec:first-child {
  margin-top: 0;
}

.sec.line {
  margin-top: 6px;
  border-top: 1px solid #f1f5f9;
}

.sec svg {
  color: var(--color-text-sub);
}

.sec .n {
  font-size: 12.5px;
  font-weight: 400;
  color: var(--color-text-weak);
}

.sec .right,
.right {
  margin-left: auto;
}

.sec .pct {
  margin-left: auto;
  font-weight: 500;
  color: var(--color-text-sub);
}

.pbar {
  height: 6px;
  overflow: hidden;
  border-radius: 3px;
  background: #e2e8f0;
}

.pbar i {
  display: block;
  height: 100%;
  background: var(--color-primary);
  transition: width 250ms linear;
}

.sks {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 22px;
}

.skr {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sk {
  display: block;
  border-radius: 4px;
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: sk 1.2s ease-in-out infinite;
}

.sk.a {
  width: 70px;
  height: 14px;
}

.sk.b {
  flex: 1;
  height: 32px;
}

.sk.c {
  width: 84px;
  height: 8px;
}

@keyframes sk {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

.foot {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  font-size: 12.5px;
  line-height: 19px;
  color: var(--color-text-weak);
}

.state {
  position: absolute;
  inset: 84px 0 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-weak);
}

.state b {
  font-size: 14px;
  color: var(--color-text-sub);
}

.state.err svg {
  color: var(--color-level-alarm);
}

/* 결과 스크롤 영역 (아래 안내·단추 자리는 비워 둠) */
.fsc {
  position: absolute;
  left: 0;
  right: -8px;
  top: 84px;
  bottom: 96px;
  overflow-y: auto;
  padding-right: 8px;
  scrollbar-width: thin;
}

.fsh {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 92px;
  height: 34px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 4px;
  padding: 0 0 4px;
  border: none;
  background: linear-gradient(rgba(255, 255, 255, 0), #fff 60%);
  font-size: 12px;
  color: var(--color-text-sub);
  cursor: pointer;
}

.fsh svg {
  animation: nudge 1.4s ease-in-out infinite;
}

@keyframes nudge {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(3px);
  }
}

.gas {
  width: 100%;
  height: 200px;
}

/* 입력 줄 */
.fr {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 10px;
  min-height: 43px;
  padding: 5px 0;
  border-radius: var(--radius-sm);
  font-size: 13.5px;
  transition: background 150ms;
}

.fr:focus-within {
  background: var(--color-primary-soft);
}

.fr label {
  width: 78px;
  flex: none;
  color: var(--color-text-sub);
}

.in {
  flex: 1;
  min-width: 0;
  height: 32px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: 5px;
  background: #fff;
  transition:
    border-color 150ms,
    background 200ms;
}

.in input {
  flex: 1;
  min-width: 0;
  border: none;
  background: none;
  font: inherit;
  font-weight: 500;
  color: var(--color-text);
  outline: none;
}

.in.num input {
  font-family: var(--font-mono);
  font-size: 13px;
}

.in:hover {
  border-color: var(--color-text-weak);
}

.in:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.in.edt {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.unit {
  font-size: 12px;
  color: var(--color-text-weak);
}

/* 확인 필요: 주황 테두리 (확인하면 200ms 에 회색으로) */
.fr.lo .in {
  border: 2px solid var(--color-level-warn);
  background: #fff7ed;
}

.fr.lo label {
  font-weight: 600;
  color: var(--color-level-warn-text);
}

.okb {
  width: 92px;
  height: 32px;
  flex: none;
  padding: 0;
  border-color: var(--color-level-warn);
  background: #fff;
  font-size: 13px;
  color: var(--color-level-warn-text);
}

.okb:hover {
  border-color: var(--color-level-warn);
  background: var(--color-level-warn);
  color: #fff;
}

.cf,
.cf-space {
  width: 92px;
  flex: none;
}

.cf {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-level-normal-text);
  animation: softin 300ms both;
}

@keyframes softin {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.why {
  width: 100%;
  padding-left: 88px;
  font-size: 12px;
  color: var(--color-level-warn-text);
}

.fadein {
  animation: fade-up 300ms ease-out both;
}

@keyframes fade-up {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* 사전 안전조치 */
.pre > div {
  min-height: 38px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
  border-bottom: 1px solid #f1f5f9;
  font-size: 13.5px;
}

.pre > div:last-child {
  border: none;
}

.no {
  width: 20px;
  height: 20px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #f1f5f9;
  font-size: 11px;
  color: var(--color-text-sub);
}

.pre .tx {
  flex: 1;
}

.pre small {
  display: block;
  font-size: 12px;
  color: var(--color-text-weak);
}

.pre em {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--color-level-normal-soft);
  font-size: 12.5px;
  font-style: normal;
  font-weight: 700;
  color: var(--color-level-normal-text);
}

/* 아래 안내·단추 */
.hint {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 56px;
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-level-warn-text);
}

.hint.ok {
  color: var(--color-level-normal-text);
}

.hint.weak,
.weak {
  font-weight: 400;
  color: var(--color-text-weak);
}

.acts {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 8px;
}

.acts .btn {
  height: 44px;
}

.save {
  padding: 0 22px;
}

/* 확인을 다 하면 저장 단추가 한 번 튐 */
.save.ready:not(:disabled) {
  animation: pop 400ms cubic-bezier(0.2, 1.2, 0.3, 1);
}

@keyframes pop {
  0% {
    transform: scale(0.94);
  }
  100% {
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .sk,
  .fsh svg,
  .fadein,
  .save.ready {
    animation: none;
  }
}
</style>
