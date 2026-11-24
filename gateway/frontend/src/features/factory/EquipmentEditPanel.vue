<!--
  설비 정보 (화면설계안 vLayout() panel())
  - 설비 이름, 종류(DB equipment_type 코드 — 화면에는 한글 이름),
    위치 X · Y (DB pos_x · pos_y, 배치도 화면 좌표 — 숫자로 고치면 배치도도 옮겨짐)
  - 사용 상태 · 배기팬 자동 제어 스위치는 2026-11-19 '설비 기준값 관리' 창의 '설비 설정'으로 옮김 (재원님 요청)
    (사용 상태는 배치도 카드 위쪽 ON/OFF 로도 바꿀 수 있음 — 그쪽은 '저장'으로 배치와 함께 저장)
  - 담당자 · 연결된 센서(지금 값·단계) — 보기만
  - '설비 기준값 관리' 단추 → thresholds 이벤트 → 화면이 기준값 관리 창을 엶 (예전 '설비 기준값 관리' 메뉴를 여기로 옮김)
    카드 아래쪽에 고정된 파란 단추 (2026-11-19 재원님 요청: 잘 보이게, 내용이 길어 스크롤해도 늘 보임)
  - 이 설비의 변경 내용 (저장 전)
  - 고른 설비가 없으면 '선택한 설비가 없습니다'
  - 새 설비(저장 전, eq.isNew)는 기준값 관리 단추 대신 '저장하면 기준값을 정할 수 있습니다' (서버 id 가 없어서)
  - '설비 삭제' 단추 → remove 이벤트 (2026-11-20). 지울 설비(eq.deleting)는 '삭제 예정' 안내 + '삭제 취소'
    새 설비는 '설비 빼기' (저장 전이라 바로 빠짐)
  - 바꾼 값은 change(필드, 값) 로 화면에 알림 → 화면이 저장 전 변경에 모음
-->
<script setup>
import { computed } from 'vue'
import { EQUIPMENT_TYPES, typeLabel as equipmentTypeLabel } from './layoutDraft'
import AppIcon from '@/components/AppIcon.vue'
import { LEVEL_META, SENSOR_TYPES } from '@/types/codes'
import { toast } from '@/utils/toast'

const props = defineProps({
  /** 고른 설비 { id, key, name, type, active, pos, sensors, managers } (지금 값) — 없으면 null */
  eq: { type: Object, default: null },
  /** 지금 보이는 위치 { x, y } (pos 가 없으면 그림에 그려진 자리) */
  place: { type: Object, default: null },
  /** 이 설비의 저장 전 변경 [{ type, title, detail }] */
  changes: { type: Array, default: () => [] },
})

const emit = defineEmits(['change', 'thresholds', 'remove'])

const typeLabel = (t) => SENSOR_TYPES.find((x) => x.type === t)?.label ?? t
const TYPE_BADGE = { move: '위치 변경됨', name: '이름 변경됨', meta: '정보 변경됨', add: '새 설비', del: '삭제 예정' }
/** 옮긴 설비면 위치 칸을 파랗게 */
const moved = computed(() => props.changes.some((c) => c.type === 'move'))

function onName(e) {
  const v = e.target.value.trim()
  if (!v) {
    e.target.value = props.eq.name
    toast('설비 이름을 입력해 주세요.', { type: 'warn' })
    return
  }
  emit('change', 'name', v)
}

/** 위치 X · Y 숫자 (배치도 화면 좌표) → pos { x, y } */
function onPos(axis, e) {
  const v = Number(e.target.value)
  if (e.target.value === '' || !Number.isFinite(v) || !props.place) {
    toast('위치는 숫자로 입력해 주세요.', { type: 'warn' })
    e.target.value = props.place ? Math.round(props.place[axis]) : ''
    return
  }
  emit('change', 'pos', { ...props.place, [axis]: Math.round(v * 10) / 10 })
}

/** 종류 고르기 목록: 아는 코드 + (지금 값이 목록에 없거나 비어 있으면) 그 값 한 줄 */
const typeOptions = computed(() => {
  const t = props.eq?.type ?? null
  const known = EQUIPMENT_TYPES.map((x) => ({ value: x.code, label: x.label }))
  return EQUIPMENT_TYPES.some((x) => x.code === t) ? known : [{ value: t ?? '', label: equipmentTypeLabel(t) }, ...known]
})
</script>

<template>
  <section class="pcard base-card rise">
    <header class="ch">
      <h3>설비 정보</h3>
      <span v-if="changes.length" class="m-chip info badge">{{ TYPE_BADGE[changes[0].type] }}</span>
    </header>

    <div v-if="!eq" class="empty">
      <AppIcon name="layers" :size="26" />
      <b>선택한 설비가 없습니다</b>
      배치도나 목록에서 설비를 선택하세요.<br />끌어서 위치를 옮길 수 있습니다.
    </div>

    <div v-else :key="eq.id" class="pn">
      <label class="fl" for="eq-name">설비 이름</label>
      <input id="eq-name" class="inp" :value="eq.name" maxlength="30" @change="onName" @keydown.enter="$event.target.blur()" />

      <label class="fl" for="eq-kind">종류</label>
      <select id="eq-kind" class="inp" :value="eq.type ?? ''" @change="emit('change', 'type', $event.target.value || null)">
        <option v-for="o in typeOptions" :key="o.value" :value="o.value" :disabled="!o.value">{{ o.label }}</option>
      </select>

      <div class="fl">위치 <small>배치도 좌표</small></div>
      <div class="pos">
        <label :class="{ edt: moved }"><span>X</span><input type="number" step="1" class="mono" :value="place ? Math.round(place.x) : ''" @change="onPos('x', $event)" /></label>
        <label :class="{ edt: moved }"><span>Y</span><input type="number" step="1" class="mono" :value="place ? Math.round(place.y) : ''" @change="onPos('y', $event)" /></label>
      </div>

      <div class="fl">담당자 <small>{{ eq.managers.length }}명</small></div>
      <div class="mgs">
        <span v-for="m in eq.managers" :key="m.name" class="mg"><i>{{ m.name[0] }}</i>{{ m.name }}</span>
        <span v-if="!eq.managers.length" class="weak">지정된 담당자 없음</span>
      </div>

      <div class="fl">센서 <small>{{ eq.sensors.length }}개</small></div>
      <div v-if="eq.sensors.length" class="sens">
        <div v-for="s in eq.sensors" :key="s.tag" class="sn">
          <b class="mono">{{ s.tag }}</b>
          <span>{{ typeLabel(s.type) }}</span>
          <span class="mono val">{{ s.value ?? '–' }} {{ s.unit }}</span>
          <span class="lv" :style="{ background: LEVEL_META[s.level]?.color }" :title="LEVEL_META[s.level]?.label" />
        </div>
      </div>
      <p v-else class="weak">연결된 센서가 없습니다</p>

      <div class="fl">변경 내용</div>
      <TransitionGroup v-if="changes.length" tag="div" name="chg" class="chgs">
        <div v-for="c in changes" :key="c.type" class="ci">
          <AppIcon :name="c.type === 'move' ? 'layers' : 'edit'" :size="15" />
          <span><b>{{ c.title }}</b><small>{{ c.detail }}</small></span>
        </div>
      </TransitionGroup>
      <p v-else class="weak">바꾼 내용이 없습니다.</p>
    </div>

    <!-- 기준값 관리 창 열기 (센서별 주의·경보 기준, 계절, 변경 이력, 사용 상태 · 배기팬 자동 제어) -->
    <!-- 새 설비(저장 전): 기준값은 저장한 뒤에 -->
    <div v-if="eq?.isNew" class="act">
      <p class="weak">저장하면 이 설비의 기준값을 정할 수 있습니다.</p>
      <button type="button" class="btn rm-btn" @click="emit('remove')"><AppIcon name="x" :size="16" />설비 빼기</button>
    </div>
    <!-- 지울 설비(저장 전) -->
    <div v-else-if="eq?.deleting" class="act">
      <p class="weak">저장하면 이 설비를 지웁니다. 센서가 붙은 설비는 지우지 않고 '사용 안 함'으로 바뀝니다.</p>
      <button type="button" class="btn rm-btn" @click="emit('remove')"><AppIcon name="undo" :size="16" />삭제 취소</button>
    </div>
    <div v-else-if="eq" class="act">
      <button type="button" class="btn btn-primary th-btn" @click="emit('thresholds')">
        <AppIcon name="slider" :size="17" />설비 기준값 관리
      </button>
      <button type="button" class="btn rm-btn danger" @click="emit('remove')"><AppIcon name="x" :size="16" />설비 삭제</button>
    </div>

    <footer class="bt">
      <AppIcon name="info" :size="15" />저장하면 대시보드 배치도에 바로 반영됩니다
    </footer>
  </section>
</template>

<style scoped>
.pcard {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--color-border-card);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.ch {
  height: 48px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 20px;
  border-bottom: 1px solid var(--color-border);
}

.ch h3 {
  font-size: 16px;
  font-weight: 700;
}

.badge {
  margin-left: auto;
  animation: pop-in var(--m-pop-in) both;
}

.empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  line-height: 20px;
  color: var(--color-text-weak);
  text-align: center;
}

.empty b {
  font-size: 14px;
  color: var(--color-text-sub);
}

/* 다른 설비를 고르면 오른쪽에서 밀려 들어옴 */
.pn {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 18px 20px;
  animation: pn-in var(--m-panel) both;
}

@keyframes pn-in {
  from {
    opacity: 0;
    transform: translateX(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}


.fl {
  display: block;
  margin: 16px 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.fl small {
  margin-left: 4px;
  font-weight: 400;
  color: var(--color-text-weak);
}

.inp {
  width: 100%;
  height: 38px;
  padding: 0 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 13.5px;
  color: var(--color-text);
}

/* 위치 X · Y: 두 칸이 카드 너비를 반씩 나눔 (min-width 0 → 숫자 칸이 기본 너비로 버티며 옆으로 넘치지 않게) */
.pos {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.pos label {
  min-width: 0;
  height: 38px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  transition:
    border-color 150ms,
    background 200ms;
}

/* 옮긴 축은 파랗게 */
.pos label.edt {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.pos span {
  font-size: 12px;
  color: var(--color-text-weak);
}

.pos input {
  flex: 1;
  width: 0;
  min-width: 0;
  border: none;
  background: none;
  font-size: 13.5px;
  outline: none;
}

.mgs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mg {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 10px 0 4px;
  border: 1px solid var(--color-border);
  border-radius: 15px;
  font-size: 13px;
}

.mg i {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #eef2f6;
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
  color: var(--color-text-sub);
}

.sens {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.sn {
  height: 38px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border-top: 1px solid #f1f5f9;
  font-size: 13px;
}

.sn:first-child {
  border-top: none;
}

.sn b {
  width: 56px;
  font-size: 12.5px;
}

.val {
  margin-left: auto;
  color: var(--color-text-sub);
}

.lv {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.chgs {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ci {
  display: flex;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-soft);
  font-size: 13px;
}

.ci svg {
  flex: none;
  margin-top: 2px;
  color: var(--color-primary);
}

.ci span {
  display: flex;
  flex-direction: column;
}

.ci small {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-sub);
}

.chg-enter-active {
  transition:
    opacity 250ms,
    transform 250ms;
}

.chg-enter-from {
  opacity: 0;
  transform: translateY(-4px);
}

.weak {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-weak);
}

/* 설비 기준값 관리 단추: 카드 아래 고정, 가로 가득, 파란색 */
.act {
  flex: none;
  padding: 14px 20px;
  border-top: 1px solid var(--color-border);
}

.th-btn {
  width: 100%;
  height: 44px;
  font-size: 14.5px;
}

/* 설비 삭제 · 빼기 · 삭제 취소: 기준값 관리 아래 낮은 단추 (삭제는 빨간 글씨) */
.rm-btn {
  width: 100%;
  height: 36px;
  margin-top: 8px;
  font-size: 13.5px;
}

.rm-btn.danger {
  border-color: transparent;
  background: none;
  color: var(--color-level-alarm-text);
}

.rm-btn.danger:hover {
  background: var(--color-level-alarm-soft);
}

.bt {
  flex: none;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 20px 18px;
  border-top: 1px solid var(--color-border);
  font-size: 12.5px;
  color: var(--color-text-weak);
}

@media (prefers-reduced-motion: reduce) {
  .pn {
    animation: none;
  }
}
</style>
